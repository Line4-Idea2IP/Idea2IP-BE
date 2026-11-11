package com.line4.idea2ip.domain.contribution;

import com.line4.idea2ip.domain.artwork.repository.FinalArtworkRepository;
import com.line4.idea2ip.domain.contribution.ContributionModels.*;
import com.line4.idea2ip.domain.interview.entity.AiInterview;
import com.line4.idea2ip.domain.interview.repository.AiInterviewRepository;
import com.line4.idea2ip.domain.material.repository.CreativeMaterialRepository;
import com.line4.idea2ip.domain.project.entity.Project;
import com.line4.idea2ip.domain.project.entity.ProjectInputChange.Operation;
import com.line4.idea2ip.domain.project.entity.ProjectInputChange.Type;
import com.line4.idea2ip.domain.project.repository.ProjectRepository;
import com.line4.idea2ip.domain.project.service.ProjectInputChanges;
import com.line4.idea2ip.domain.project.service.ProjectService;
import com.line4.idea2ip.global.job.PersistentJob.Status;
import com.line4.idea2ip.global.job.WorkflowException;
import com.line4.idea2ip.integration.ai.BriefAiClient.*;
import com.line4.idea2ip.integration.ai.ProjectBriefInput;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ContributionService {
    private static final List<Status> ACTIVE = List.of(Status.QUEUED, Status.RUNNING);

    private final ProjectService projects;
    private final ProjectRepository projectRepository;
    private final ProjectInputChanges changes;
    private final AiInterviewRepository interviews;
    private final CreativeMaterialRepository materials;
    private final FinalArtworkRepository artworks;
    private final ContributionReviewRepository reviews;
    private final ContributionStepRepository steps;
    private final ContributionGenerationRepository generations;
    private final ProjectBriefInput inputs;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public View get(Long userId, Long projectId) {
        projects.getOwnedProject(userId, projectId);
        return view(projectId, reviews.findByProject_ProjectId(projectId).orElse(null));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Detail detail(Long userId, Long projectId, Long stepId) {
        projects.getOwnedProject(userId, projectId);
        var review = reviews.findByProject_ProjectId(projectId).orElse(null);
        return timeline(projectId, review).stream().filter(item -> item.stepId().equals(stepId)).findFirst()
                .orElseThrow(() -> new WorkflowException("CONTRIBUTION_STEP_NOT_FOUND", 404, "기여 단계를 찾을 수 없습니다."));
    }

    @Transactional
    public GenerationView generate(Long userId, Long projectId, GenerateReq req) {
        Project project = projects.getOwnedProjectForUpdate(userId, projectId);
        requireInterview(projectId);
        ContributionReview review = getOrCreate(project);
        var existing = generations.findByReview_IdAndRequestId(review.getId(), req.requestId()).orElse(null);
        if (existing != null) {
            if (existing.getExpectedRevision() != req.expectedRevision()) {
                throw conflict("REQUEST_ID_CONFLICT", "같은 요청 식별자에 다른 입력을 보낼 수 없습니다.");
            }
            if (req.retryRequested() && existing.getJob().failure() != null && existing.getJob().failure().retryable()) {
                ensureIdle(review);
                existing.getJob().retry();
            }
            return toView(existing);
        }

        if (req.retryRequested()) {
            throw conflict("RETRY_REQUEST_NOT_FOUND", "재시도할 생성 요청을 찾을 수 없습니다.");
        }
        checkRevision(review, req.expectedRevision());
        ensureIdle(review);
        return toView(generations.save(new ContributionGeneration(review, req.requestId(),
                req.expectedRevision(), inputs.capture(project))));
    }

    @Transactional
    public View review(Long userId, Long projectId, PatchReq req) {
        Project project = projects.getOwnedProjectForUpdate(userId, projectId);
        requireInterview(projectId);
        ContributionReview review = getOrCreate(project);
        checkRevision(review, req.expectedRevision());
        var stored = stored(review);
        var available = timeline(projectId, review);
        Set<Long> seen = new HashSet<>();
        boolean changed = false;
        for (Change change : req.changes()) {
            if (!seen.add(change.stepId()) || (change.decision() == Decision.EDIT && change.edits() == null)
                    || (change.decision() == Decision.EXCLUDE && change.edits() != null)) {
                throw new WorkflowException("INVALID_CONTRIBUTION_CHANGE", 400, "기여 단계 변경 내용을 확인해 주세요.");
            }
            Detail detail = available.stream().filter(item -> item.stepId().equals(change.stepId())).findFirst()
                    .orElseThrow(() -> new WorkflowException("CONTRIBUTION_STEP_NOT_FOUND", 404, "기여 단계를 찾을 수 없습니다."));
            var step = stored.stream().filter(item -> publicId(item).equals(detail.stepId())).findFirst()
                    .orElseGet(() -> addStep(review, stored, detail));
            changed |= step.apply(change.decision(), change.edits());
        }
        String reason = StringUtils.hasText(req.noContributionReason()) ? req.noContributionReason().strip() : null;
        if (req.finalizeRequested()) {
            if (stored.stream().anyMatch(ContributionStep::needsReview)) {
                throw new WorkflowException("CONTRIBUTION_DECISION_REQUIRED", 400, "검토 대상의 확정 또는 제외를 선택해 주세요.");
            }
            if (stored.stream().noneMatch(this::hasConfirmedContent) && reason == null) {
                throw new WorkflowException("NO_CONTRIBUTION_REASON_REQUIRED", 400, "확정할 기여가 없는 이유를 입력해 주세요.");
            }
            changed |= review.getStatus() != ReviewStatus.CONFIRMED
                    || !Objects.equals(review.getNoContributionReason(), reason);
        }
        if (changed) {
            review.review(req.finalizeRequested(), req.finalizeRequested() ? reason : null);
            changes.record(project, Type.CONTRIBUTION, Operation.UPDATED, review.getId());
        }
        return view(projectId, review);
    }

    private ContributionStep addStep(ContributionReview review, List<ContributionStep> stored, Detail detail) {
        long resourceId = detail.kind() == Kind.MATERIAL ? detail.materialRefs().getFirst().materialId()
                : detail.finalArtworkRef().artworkId();
        ContributionStep step = steps.save(new ContributionStep(review, detail.kind(), resourceId, detail.title()));
        String id = (detail.kind() == Kind.MATERIAL ? "material:" : "artwork:") + resourceId;
        List<Evidence> evidence = inputs.capture(review.getProject()).sources().stream()
                .filter(source -> source.sourceId().equals(id)).map(source -> Evidence.from(source, null)).toList();
        step.propose(new Content(detail.aiUsage(), detail.humanCreatedElements(), detail.humanEdits()), evidence);
        stored.add(step);
        return step;
    }

    public boolean confirmed(Long projectId) {
        return reviews.findByProject_ProjectId(projectId)
                .filter(review -> review.getStatus() == ReviewStatus.CONFIRMED).isPresent();
    }

    public List<Confirmed> confirmedSteps(Long projectId) {
        var review = reviews.findByProject_ProjectId(projectId).orElse(null);
        return stored(review).stream().filter(this::hasConfirmedContent)
                .map(step -> new Confirmed(step.getTitle(), step.getContent(), step.evidence())).toList();
    }

    public String noContributionReason(Long projectId) {
        return reviews.findByProject_ProjectId(projectId).map(ContributionReview::getNoContributionReason).orElse(null);
    }

    public List<Long> runnable() { return generations.findRunnable(Status.QUEUED, Status.RUNNING, Instant.now(), Limit.of(4)); }

    @Transactional
    public Work claim(Long id) {
        var task = lockTask(id);
        var token = task.getJob().claim(Instant.now());
        return token == null ? null : new Work(id, token, task.getInput());
    }

    @Transactional
    public void finish(Work work, CandidateResult candidates) {
        var task = lockTask(work.id());
        if (!task.getJob().owns(work.token())) return;
        var review = task.getReview();
        if (!sameSourceInput(work.input(), inputs.capture(review.getProject()))) {
            task.getJob().fail("AI_INPUT_INVALID", false);
            return;
        }
        var stored = stored(review);
        Map<Key, Proposal> proposals = proposals(work.input(), candidates.candidates());
        boolean changed = false;
        for (var entry : proposals.entrySet()) {
            var key = entry.getKey();
            var proposal = entry.getValue();
            var step = stored.stream().filter(item -> item.getKind() == key.kind()
                    && item.getResourceId().equals(key.resourceId())).findFirst()
                    .orElseGet(() -> steps.save(new ContributionStep(review, key.kind(), key.resourceId(), proposal.title)));
            changed |= step.propose(proposal.content(), List.copyOf(proposal.evidence.values()));
        }
        for (var step : stored) {
            if (!proposals.containsKey(new Key(step.getKind(), step.getResourceId()))
                    && step.getStatus() == StepStatus.UNREVIEWED) {
                changed |= step.propose(Content.empty(), List.of());
            }
        }
        review.generated(changed, candidates.unresolvedFields());
        if (changed) changes.record(review.getProject(), Type.CONTRIBUTION, Operation.UPDATED, review.getId());
        task.getJob().succeed();
    }

    @Transactional
    public void fail(Work work, String code, boolean retryable) {
        var task = lockTask(work.id());
        if (task.getJob().owns(work.token())) task.getJob().fail(code, retryable);
    }

    private boolean sameSourceInput(Input saved, Input current) {
        return saved.title().equals(current.title()) && saved.sources().equals(current.sources());
    }

    private ContributionGeneration lockTask(Long id) {
        projectRepository.findByIdForUpdate(generations.projectId(id).orElseThrow()).orElseThrow();
        return generations.findById(id).orElseThrow();
    }

    private Map<Key, Proposal> proposals(Input input, List<Candidate> candidates) {
        Map<String, Source> sources = new HashMap<>();
        input.sources().forEach(source -> sources.put(source.sourceId(), source));
        Map<Key, Proposal> results = new LinkedHashMap<>();
        List<Candidate> grounded = new ArrayList<>(candidates);
        for (var source : input.sources()) {
            if (source.messageId() != null && source.locator().endsWith("(HUMAN_CREATED_ELEMENT)")
                    && !Set.of("없음", "없어요", "없습니다", "해당 없음").contains(source.content().replaceFirst("[.]$", "").strip())) {
                grounded.add(new Candidate("human_created", source.content(), List.of(source.sourceId())));
            }
        }
        for (var candidate : grounded) {
            for (String id : candidate.sourceIds()) {
                var source = sources.get(id);
                if (source == null || source.messageId() == null || !source.content().contains(candidate.statement())) {
                    throw new Failure("AI_RESULT_INVALID", true);
                }
                boolean directEdit = candidate.category().equals("human_edit") && "human_edit".equals(source.targetField());
                boolean directCreation = source.locator().endsWith("(HUMAN_CREATED_ELEMENT)")
                        && !Set.of("없음", "없어요", "없습니다", "해당 없음").contains(source.content().replaceFirst("[.]$", "").strip());
                if (!directEdit && !directCreation) continue;
                var proposal = proposal(results, sources, source);
                var statements = directEdit ? proposal.edits : proposal.elements;
                statements.add(candidate.statement());
                proposal.evidence.putIfAbsent(id, Evidence.from(source, candidate.statement()));
            }
        }
        for (var source : input.sources()) {
            if (source.messageId() == null || !"ai_usage".equals(source.targetField())) continue;
            var proposal = proposal(results, sources, source);
            if (source.locator().endsWith("(AI_TOOL)")) proposal.tools.add(source.content());
            if (source.locator().endsWith("(AI_PURPOSE)")) proposal.purposes.add(source.content());
            if (source.locator().endsWith("(AI_OUTPUT_USAGE)")) proposal.outputs.add(source.content());
            proposal.evidence.putIfAbsent(source.sourceId(), Evidence.from(source, source.content()));
        }
        return results;
    }

    private Proposal proposal(Map<Key, Proposal> results, Map<String, Source> sources, Source source) {
        var material = source.materialId() == null ? null : sources.get("material:" + source.materialId());
        var key = material == null ? new Key(Kind.INTERVIEW_STATEMENT, source.messageId())
                : new Key(Kind.MATERIAL, source.materialId());
        var proposal = results.computeIfAbsent(key, ignored -> new Proposal(material == null ? source.title() : material.title()));
        if (material != null) proposal.evidence.putIfAbsent(material.sourceId(), Evidence.from(material, null));
        return proposal;
    }

    private View view(Long projectId, ContributionReview review) {
        var timeline = timeline(projectId, review).stream()
                .map(item -> new Summary(item.stepId(), item.order(), item.kind(), item.title(), item.reviewStatus())).toList();
        if (review == null) return new View(ReviewStatus.NOT_STARTED, 0, timeline, List.of(), null);
        var latest = generations.findFirstByReview_IdOrderByUpdateAtDescIdDesc(review.getId()).map(this::toView).orElse(null);
        return new View(review.getStatus(), review.getRevision(), timeline, review.getUnresolvedFields(), latest);
    }

    private List<Detail> timeline(Long projectId, ContributionReview review) {
        var entries = stored(review);
        List<Detail> list = new ArrayList<>();
        for (var material : materials.findAllByProject_ProjectIdOrderByMaterialOrderAsc(projectId)) {
            var step = match(entries, Kind.MATERIAL, material.getMaterialId());
            var files = material.getFiles().stream().map(file -> new FileRef(file.getFileId(), file.getOriginalFileName(),
                    "/api/materials/" + material.getMaterialId() + "/files/" + file.getFileId())).toList();
            var refs = List.of(new MaterialRef(material.getMaterialId(), material.getTitle(), files));
            var ai = StringUtils.hasText(material.getUsedAi()) ? new AiUsage(List.of(material.getUsedAi()), null, null) : null;
            list.add(detail(list.size() + 1, Kind.MATERIAL, material.getMaterialId(), material.getTitle(), step, refs, null, ai));
        }
        var artwork = artworks.findByProject_ProjectId(projectId).orElse(null);
        for (var step : entries) {
            if (artwork != null && step.getKind() == Kind.FINAL_ARTWORK && step.getResourceId().equals(artwork.getArtworkId())) continue;
            if (list.stream().noneMatch(item -> item.stepId().equals(publicId(step)))
                    && (step.getStatus() != StepStatus.UNREVIEWED || step.getContent().hasContribution())) {
                list.add(detail(list.size() + 1, step.getKind(), step.getResourceId(), step.getTitle(), step, List.of(), null, null));
            }
        }
        if (artwork != null) {
            var ref = new ArtworkRef(artwork.getArtworkId(), artwork.getTitle(), artwork.getImagePath() == null ? null
                    : "/api/projects/" + projectId + "/artwork/image");
            list.add(detail(list.size() + 1, Kind.FINAL_ARTWORK, artwork.getArtworkId(), artwork.getTitle(),
                    match(entries, Kind.FINAL_ARTWORK, artwork.getArtworkId()), List.of(), ref, null));
        }
        return List.copyOf(list);
    }

    private Detail detail(int order, Kind kind, Long resourceId, String title, ContributionStep step,
                          List<MaterialRef> refs, ArtworkRef artwork, AiUsage defaultAi) {
        var content = step == null ? Content.empty() : step.getContent();
        return new Detail(step == null ? linkedId(kind, resourceId) : publicId(step), order, kind, title,
                step == null ? StepStatus.UNREVIEWED : step.getStatus(), refs, artwork,
                content.aiUsage() == null ? defaultAi : content.aiUsage(), content.humanCreatedElements(),
                content.humanEdits(), step == null ? List.of() : step.evidence());
    }

    private ContributionStep match(List<ContributionStep> entries, Kind kind, Long resourceId) {
        return entries.stream().filter(step -> step.getKind() == kind && step.getResourceId().equals(resourceId))
                .findFirst().orElse(null);
    }

    // Linked steps are visible before a review exists, so their public IDs do not depend on a persisted step.
    private Long linkedId(Kind kind, Long resourceId) { return -resourceId * 2 - (kind == Kind.FINAL_ARTWORK ? 1 : 0); }

    private Long publicId(ContributionStep step) {
        return step.getKind() == Kind.INTERVIEW_STATEMENT ? step.getId() : linkedId(step.getKind(), step.getResourceId());
    }

    private boolean hasConfirmedContent(ContributionStep step) {
        return step.getStatus() == StepStatus.CONFIRMED && step.getContent().hasContribution();
    }

    private List<ContributionStep> stored(ContributionReview review) {
        return review == null ? new ArrayList<>() : new ArrayList<>(steps.findByReview_IdOrderById(review.getId()));
    }

    private ContributionReview getOrCreate(Project project) {
        return reviews.findByProject_ProjectId(project.getProjectId()).orElseGet(() -> reviews.save(new ContributionReview(project)));
    }

    private void requireInterview(Long projectId) {
        if (interviews.findByProject_ProjectId(projectId).filter(i -> i.getStatus() == AiInterview.Status.COMPLETED).isEmpty()) {
            throw conflict("INTERVIEW_NOT_COMPLETED", "AI 인터뷰를 먼저 완료해 주세요.");
        }
    }

    private void checkRevision(ContributionReview review, long expected) {
        if (review.getRevision() != expected) throw conflict("STALE_REVIEW_REVISION", "최신 검토 내용을 다시 불러와 주세요.");
    }

    private void ensureIdle(ContributionReview review) {
        if (generations.existsByReview_IdAndJob_StatusIn(review.getId(), ACTIVE)) {
            throw conflict("CONTRIBUTION_JOB_IN_PROGRESS", "기여 후보 생성이 진행 중입니다.");
        }
    }

    private WorkflowException conflict(String code, String message) { return new WorkflowException(code, 409, message); }

    private GenerationView toView(ContributionGeneration item) {
        return new GenerationView(item.getId(), item.getRequestId(), item.getJob().getStatus(), item.getJob().failure());
    }

    public record Work(Long id, UUID token, Input input) {}
    private record Key(Kind kind, Long resourceId) {}

    private static final class Proposal {
        private final String title;
        private final Set<String> elements = new LinkedHashSet<>();
        private final Set<String> edits = new LinkedHashSet<>();
        private final Set<String> tools = new LinkedHashSet<>();
        private final Set<String> purposes = new LinkedHashSet<>();
        private final Set<String> outputs = new LinkedHashSet<>();
        private final Map<String, Evidence> evidence = new LinkedHashMap<>();

        private Proposal(String title) { this.title = title; }

        private Content content() {
            AiUsage aiUsage = tools.isEmpty() && purposes.isEmpty() && outputs.isEmpty() ? null
                    : new AiUsage(List.copyOf(tools), purposes.isEmpty() ? null : String.join("\n", purposes),
                    outputs.isEmpty() ? null : String.join("\n", outputs));
            return new Content(aiUsage, List.copyOf(elements), List.copyOf(edits));
        }
    }
}
