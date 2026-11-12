package com.line4.idea2ip.domain.report;

import com.line4.idea2ip.domain.artwork.repository.FinalArtworkRepository;
import com.line4.idea2ip.domain.contribution.ContributionService;
import com.line4.idea2ip.domain.interview.entity.AiInterview;
import com.line4.idea2ip.domain.interview.entity.InterviewField;
import com.line4.idea2ip.domain.interview.entity.InterviewFieldState;
import com.line4.idea2ip.domain.interview.repository.AiInterviewRepository;
import com.line4.idea2ip.domain.material.repository.CreativeMaterialRepository;
import com.line4.idea2ip.domain.material.entity.MaterialType;
import com.line4.idea2ip.domain.project.entity.Project;
import com.line4.idea2ip.domain.project.repository.ProjectRepository;
import com.line4.idea2ip.domain.project.service.ProjectInputChanges;
import com.line4.idea2ip.domain.project.service.ProjectService;
import com.line4.idea2ip.domain.report.ReportModels.*;
import com.line4.idea2ip.domain.report.pdf.ReportPdfStorage;
import com.line4.idea2ip.global.job.PersistentJob;
import com.line4.idea2ip.global.job.WorkflowException;
import com.line4.idea2ip.integration.ai.ProjectBriefInput;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReportService {
    private static final List<PersistentJob.Status> ACTIVE = List.of(PersistentJob.Status.QUEUED, PersistentJob.Status.RUNNING);
    private final ProjectService projects;
    private final ProjectRepository projectRepository;
    private final ProjectInputChanges changes;
    private final FinalArtworkRepository artworks;
    private final CreativeMaterialRepository materials;
    private final AiInterviewRepository interviews;
    private final ContributionService contributions;
    private final ProjectBriefInput input;
    private final ReportRepository reports;
    private final ReportVersionRepository versions;
    private final ReportPdfStorage pdfs;
    private final ReportChecklist checklist;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Readiness readiness(Long userId, Long projectId) {
        return readiness(projects.getOwnedProject(userId, projectId));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public View get(Long userId, Long projectId) {
        Project project = projects.getOwnedProject(userId, projectId);
        var report = reports.findByProject_ProjectId(projectId).orElse(null);
        if (report == null) return new View(ReportStatus.NOT_STARTED, null, null, null, false, null);
        var latest = report.getLatestReadyVersion();
        var active = active(report);
        var failed = versions.findFirstByReport_IdAndJob_StatusOrderByVersionNoDesc(report.getId(), PersistentJob.Status.FAILED)
                .orElse(null);
        var delta = latest == null ? null : changes.summarize(projectId, latest.getInputRevision(), project.getInputRevision());
        var canUpdate = latest != null && active == null && delta.hasChanges() && readiness(project).canGenerate();
        return new View(projectStatus(latest, active, failed), summary(latest), summary(active), summary(failed), canUpdate, delta);
    }

    @Transactional
    public Detail create(Long userId, Long projectId, VersionReq req) {
        Project project = projects.getOwnedProjectForUpdate(userId, projectId);
        var report = reports.findByProject_ProjectId(projectId).orElse(null);
        if (report != null) {
            var existing = versions.findByReport_IdAndRequestId(report.getId(), req.requestId()).orElse(null);
            if (existing != null) {
                if (!existing.matches(req)) throw conflict("REQUEST_ID_CONFLICT", "같은 요청 식별자에 다른 입력을 보낼 수 없습니다.");
                if (req.retryRequested() && existing.getJob().failure() != null && existing.getJob().failure().retryable()) {
                    ensureIdle(report);
                    validateBase(report, req);
                    existing.getJob().retry();
                }
                return detail(existing);
            }
            ensureIdle(report);
        }
        if (req.retryRequested()) throw conflict("RETRY_REQUEST_NOT_FOUND", "재시도할 리포트 요청이 없습니다.");

        validateRequest(req);
        ReportVersion base = report == null ? null : report.getLatestReadyVersion();
        validateBase(report, req);
        long revision;
        InputSnapshot snapshot;
        if (req.operation() == Operation.EDIT) {
            revision = base.getInputRevision();
            snapshot = base.getInputSnapshot();
        } else {
            var readiness = readiness(project);
            if (!readiness.canGenerate()) throw conflict("REPORT_NOT_READY", "리포트 생성 준비 상태를 확인해 주세요.");
            revision = project.getInputRevision();
            if (req.operation() == Operation.UPDATE && !changes.summarize(projectId, base.getInputRevision(), revision).hasChanges()) {
                throw conflict("NO_REPORT_INPUT_CHANGES", "리포트에 반영할 새 입력이 없습니다.");
            }
            var confirmed = contributions.confirmedSteps(projectId);
            // The checklist covers readiness warnings (missing material types) with more specific actions.
            snapshot = new InputSnapshot(input.capture(project), confirmed, contributions.noContributionReason(projectId),
                    checklist.items(project, confirmed, contributions.unresolvedFields(projectId)), profile(project));
        }
        if (report == null) report = reports.save(new Report(project));
        return detail(versions.save(new ReportVersion(report, req, revision, snapshot)));
    }

    @Transactional(readOnly = true)
    public VersionList list(Long userId, Long projectId, Integer before, int limit) {
        projects.getOwnedProject(userId, projectId);
        var report = reports.findByProject_ProjectId(projectId).orElse(null);
        if (report == null) return new VersionList(List.of(), null);
        var max = Limit.of(limit + 1);
        var result = before == null ? versions.findByReport_IdOrderByVersionNoDesc(report.getId(), max)
                : versions.findByReport_IdAndVersionNoLessThanOrderByVersionNoDesc(report.getId(), before, max);
        Integer next = result.size() > limit ? result.get(limit - 1).getVersionNo() : null;
        return new VersionList(result.stream().limit(limit).map(this::summary).toList(), next);
    }

    @Transactional(readOnly = true)
    public Detail getVersion(Long userId, Long projectId, Long versionId) {
        projects.getOwnedProject(userId, projectId);
        return detail(ownedVersion(projectId, versionId));
    }

    @Transactional(readOnly = true)
    public PdfDownload download(Long userId, Long projectId, Long versionId) {
        Project project = projects.getOwnedProject(userId, projectId);
        var version = ownedVersion(projectId, versionId);
        if (version.status() != Status.READY) throw conflict("PDF_NOT_READY", "PDF가 아직 준비되지 않았습니다.");
        try {
            return new PdfDownload(version.getVersionNo(), project.getTitle(),
                    pdfs.read(version.getPdfStorageKey(), version.getPdfChecksum()));
        } catch (IOException | IllegalArgumentException exception) {
            throw new WorkflowException("PDF_STORAGE_ERROR", 500, "저장된 PDF를 읽을 수 없습니다.");
        }
    }

    public List<Long> runnable() {
        return versions.findRunnable(PersistentJob.Status.QUEUED, PersistentJob.Status.RUNNING, Instant.now(), Limit.of(4));
    }

    @Transactional
    public Work claim(Long versionId) {
        var version = lockVersion(versionId);
        var token = version.getJob().claim(Instant.now());
        if (token == null) return null;
        ReportVersion base = version.getBaseVersionId() == null ? null : versions.findById(version.getBaseVersionId()).orElseThrow();
        version.stage(version.getReportContent() == null ? Stage.COLLECTING_INPUT : Stage.RENDERING_PDF);
        return new Work(versionId, version.getVersionNo(), token, version.getOperation(), version.getInputSnapshot(),
                version.getReportContent(), base == null ? null : base.getReportContent(),
                base == null ? null : base.getInputSnapshot(), version.getEdits(), version.getTemplateVersion(),
                version.getCreatedAt());
    }

    @Transactional
    public boolean stage(Work work, Stage stage) {
        var version = lockVersion(work.id());
        if (!version.getJob().owns(work.token())) return false;
        version.stage(stage);
        return true;
    }

    @Transactional
    public boolean saveDraft(Work work, Content content) {
        var version = lockVersion(work.id());
        if (!version.getJob().owns(work.token())) return false;
        version.saveDraft(content);
        version.stage(Stage.RENDERING_PDF);
        return true;
    }

    @Transactional
    public boolean publish(Work work, ReportPdfStorage.Stored stored) {
        var version = lockVersion(work.id());
        if (!version.getJob().owns(work.token())) return false;
        var latest = version.getReport().getLatestReadyVersion();
        if (!Objects.equals(latest == null ? null : latest.getId(), version.getBaseVersionId())) {
            version.getJob().fail("STALE_BASE_VERSION", false);
            return false;
        }
        version.complete(stored);
        version.getReport().publish(version);
        return true;
    }

    @Transactional
    public void fail(Work work, String code, boolean retryable) {
        var version = lockVersion(work.id());
        if (version.getJob().owns(work.token())) version.getJob().fail(code, retryable);
    }

    private Readiness readiness(Project project) {
        Long id = project.getProjectId();
        boolean artwork = artworks.findByProject_ProjectId(id).filter(value -> StringUtils.hasText(value.getImagePath())).isPresent();
        boolean interview = interviews.findByProject_ProjectId(id).filter(value -> value.getStatus() == AiInterview.Status.COMPLETED).isPresent();
        var evidence = materials.findAllByProject_ProjectIdOrderByMaterialOrderAsc(id).stream()
                .filter(value -> !value.getFiles().isEmpty()).map(value -> value.getMaterialType())
                .collect(java.util.stream.Collectors.toSet());
        boolean material = evidence.containsAll(Set.of(MaterialType.AI_ASSISTED, MaterialType.MANUAL_EDIT));
        var checks = List.of(
                check("FINAL_ARTWORK_IMAGE_REQUIRED", artwork, false, "최종 작품 이미지를 등록해 주세요."),
                check("INTERVIEW_NOT_COMPLETED", interview, false, "AI 인터뷰를 먼저 완료해 주세요."),
                check("CONTRIBUTIONS_NOT_CONFIRMED", contributions.confirmed(id), false, "창작 기여 검토를 먼저 확정해 주세요."),
                check("MATERIAL_EVIDENCE_INCOMPLETE", material, true, "첨부 이미지가 있는 제작 자료를 보완해 주세요."));
        var blocked = checks.stream().filter(item -> item.status() == CheckStatus.BLOCKED).toList();
        var warnings = checks.stream().filter(item -> item.status() == CheckStatus.WARNING).toList();
        var artworkEntity = artworks.findByProject_ProjectId(id).orElse(null);
        var interviewStates = new EnumMap<InterviewField, InterviewFieldState.Status>(InterviewField.class);
        interviews.findByProject_ProjectId(id).ifPresent(value ->
                value.getFieldStates().forEach(state -> interviewStates.put(state.field(), state.status())));
        var sections = ReportReadinessSections.sections(new ReportReadinessSections.Inputs(
                project.getArtworkType() == null ? null : project.getArtworkType().name(), project.getDescription(),
                artwork, artworkEntity == null ? null : artworkEntity.getDescription(), contributions.confirmedSteps(id),
                contributions.noContributionReason(id), interviewStates, evidence));
        return new Readiness(blocked.isEmpty(), checks, blocked, warnings, project.getInputRevision(), sections,
                ReportReadinessSections.summary(sections));
    }

    private Profile profile(Project project) {
        Long id = project.getProjectId();
        var artwork = artworks.findByProject_ProjectId(id).orElse(null);
        var facts = materials.findAllByProject_ProjectIdOrderByMaterialOrderAsc(id).stream()
                .map(material -> new MaterialFact(material.getMaterialId(), material.getMaterialType().name(),
                        material.getTitle(), material.getDescription(), material.getUsedAi(),
                        material.getProductionDate() == null ? null : material.getProductionDate().toString(),
                        material.getFiles().size(), material.getMaterialOrder()))
                .toList();
        return new Profile(project.getArtworkType() == null ? null : project.getArtworkType().name(),
                project.getDescription(), artwork == null ? null : artwork.getTitle(),
                artwork == null ? null : artwork.getDescription(), facts);
    }

    private Check check(String code, boolean ready, boolean warning, String message) {
        return new Check(code, ready ? CheckStatus.READY : warning ? CheckStatus.WARNING : CheckStatus.BLOCKED,
                ready ? "준비되었습니다." : message);
    }

    private void validateRequest(VersionReq req) {
        boolean hasBase = req.baseVersionId() != null;
        boolean edit = req.operation() == Operation.EDIT;
        if (hasBase == (req.operation() == Operation.GENERATE) || edit != !req.edits().isEmpty()
                || req.edits().stream().anyMatch(value -> !value.sectionKey().editable())
                || req.edits().stream().map(SectionEdit::sectionKey).distinct().count() != req.edits().size()) {
            throw new WorkflowException("INVALID_REPORT_REQUEST", 400, "작업 종류, 기준 버전 및 편집 항목을 확인해 주세요.");
        }
    }

    private void validateBase(Report report, VersionReq req) {
        var latest = report == null ? null : report.getLatestReadyVersion();
        if (req.operation() == Operation.GENERATE) {
            if (latest != null) throw conflict("REPORT_ALREADY_EXISTS", "최신 리포트에서 업데이트 또는 편집을 시작해 주세요.");
        } else if (latest == null || !latest.getId().equals(req.baseVersionId())) {
            throw conflict("STALE_BASE_VERSION", "최신 리포트를 다시 불러와 주세요.");
        }
    }

    private ReportStatus projectStatus(ReportVersion ready, ReportVersion active, ReportVersion failed) {
        if (ready != null) return ReportStatus.READY;
        if (active != null) return ReportStatus.GENERATING;
        return failed != null ? ReportStatus.FAILED : ReportStatus.NOT_STARTED;
    }

    private ReportVersion active(Report report) {
        return versions.findFirstByReport_IdAndJob_StatusInOrderByVersionNoDesc(report.getId(), ACTIVE).orElse(null);
    }

    private void ensureIdle(Report report) {
        var active = active(report);
        if (active != null) throw new WorkflowException("REPORT_JOB_IN_PROGRESS", 409, "리포트 생성이 진행 중입니다.", active.getId());
    }

    private ReportVersion ownedVersion(Long projectId, Long versionId) {
        return versions.findByReport_Project_ProjectIdAndId(projectId, versionId)
                .orElseThrow(() -> new WorkflowException("REPORT_VERSION_NOT_FOUND", 404, "리포트 버전을 찾을 수 없습니다."));
    }

    private ReportVersion lockVersion(Long id) {
        projectRepository.findByIdForUpdate(versions.projectId(id).orElseThrow()).orElseThrow();
        return versions.findById(id).orElseThrow();
    }

    private WorkflowException conflict(String code, String message) { return new WorkflowException(code, 409, message); }

    private Summary summary(ReportVersion version) {
        return version == null ? null : new Summary(version.getId(), version.getVersionNo(), version.getOperation(),
                version.status(), version.getStage(), version.getCreatedAt(), version.getReadyAt(), version.status() == Status.READY);
    }

    private Detail detail(ReportVersion version) {
        var content = version.getReportContent();
        var sections = content == null ? List.<SectionView>of() : content.sections().stream()
                .map(section -> new SectionView(section.sectionKey(), section.title(), section.paragraphs(),
                        section.sourceIds(), content.editedSectionKeys().contains(section.sectionKey()))).toList();
        return new Detail(version.getId(), version.getVersionNo(), version.getOperation(), version.getBaseVersionId(),
                version.status(), version.getStage(), version.getInputRevision(), version.getCreatedAt(),
                version.getUpdateAt(), version.getReadyAt(), sections, content == null ? List.of() : content.sources(),
                content == null ? List.of() : content.reviewFlags(), version.getJob().failure(), version.status() == Status.READY,
                content == null ? null : content.schemaVersion(), version.getTemplateVersion(), content == null ? null : content.mode());
    }

    record Work(Long id, int versionNo, UUID token, Operation operation, InputSnapshot input, Content draft,
                Content base, InputSnapshot baseInput, List<SectionEdit> edits, String templateVersion,
                java.time.LocalDateTime createdAt) {}
}
