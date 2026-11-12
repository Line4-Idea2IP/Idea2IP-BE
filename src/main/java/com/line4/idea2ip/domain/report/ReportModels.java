package com.line4.idea2ip.domain.report;

import com.line4.idea2ip.domain.contribution.ContributionModels.Confirmed;
import com.line4.idea2ip.domain.project.service.ProjectInputChanges;
import com.line4.idea2ip.global.job.PersistentJob;
import com.line4.idea2ip.integration.ai.BriefAiClient.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class ReportModels {
    private ReportModels() {}

    public enum Operation { GENERATE, UPDATE, EDIT }
    public enum Status { QUEUED, RUNNING, READY, FAILED }
    public enum Stage { COLLECTING_INPUT, LINKING_SOURCES, DRAFTING, RENDERING_PDF }
    public enum ReportStatus { NOT_STARTED, GENERATING, READY, FAILED }
    public enum CheckStatus { READY, BLOCKED, WARNING }

    public enum SectionKey {
        // Titles match the report screen's five items.
        WORK_OVERVIEW("신청할 저작물 전체 개요"),
        AI_USAGE("활용된 생성형 AI 산출물"),
        HUMAN_CONTRIBUTION("인간의 창작적 표현 부분 (사용자 확인)"),
        EVIDENCE_SUMMARY("창작 과정 참고자료"),
        GAPS_AND_GUIDANCE("추가 확인 및 보완사항");

        private final String title;

        SectionKey(String title) { this.title = title; }

        public String title() { return title; }

        public boolean editable() {
            return this == WORK_OVERVIEW || this == AI_USAGE || this == HUMAN_CONTRIBUTION;
        }
    }

    public record SectionEdit(@NotNull SectionKey sectionKey,
                              @NotEmpty @Size(max = 100) List<@NotBlank @Size(max = 8000) String> paragraphs) {}
    public record VersionReq(@NotNull UUID requestId, @NotNull Operation operation, @Positive Long baseVersionId,
                             @Size(max = 3) List<@NotNull @Valid SectionEdit> editedSections, Boolean retry) {
        public boolean retryRequested() { return Boolean.TRUE.equals(retry); }

        public List<SectionEdit> edits() { return editedSections == null ? List.of() : editedSections; }
    }

    public record Check(String code, CheckStatus status, String message) {}
    public record Readiness(boolean canGenerate, List<Check> checks, List<Check> blockingReasons,
                            List<Check> warnings, long inputRevision) {}
    public record Summary(Long versionId, int versionNo, Operation operation, Status status, Stage stage,
                          LocalDateTime createdAt, LocalDateTime readyAt, boolean pdfAvailable) {}
    public record VersionList(List<Summary> versions, Integer nextBeforeVersionNo) {}
    public record View(ReportStatus reportStatus, Summary latestReadyVersion, Summary activeVersion,
                       Summary lastFailedVersion, boolean canUpdate, ProjectInputChanges.Summary changeSummary) {}
    public record SectionView(String sectionKey, String title, List<String> paragraphs, List<String> sourceIds,
                              boolean userEdited) {}
    public record Flag(String sectionKey, String code, List<String> sourceIds, List<String> retainedUserParagraphs) {}
    public record Detail(Long versionId, int versionNo, Operation operation, Long baseVersionId, Status status,
                         Stage stage, long inputRevision, LocalDateTime createdAt, LocalDateTime updatedAt,
                         LocalDateTime readyAt, List<SectionView> sections, List<Evidence> sources, List<Flag> reviewFlags,
                         PersistentJob.Failure failure, boolean pdfAvailable, String schemaVersion,
                         String templateVersion, String mode) {}
    public record PdfDownload(int versionNo, byte[] bytes) {}

    public record InputSnapshot(Input aiInput, List<Confirmed> contributions, String noContributionReason,
                                List<String> warnings) {}

    public record Content(String schemaVersion, String mode, List<Section> sections, List<Evidence> sources,
                          List<String> editedSectionKeys, List<Flag> reviewFlags) {}

    public record ProjectReportStatus(ReportStatus reportStatus, Long latestReadyVersionId, boolean reportUpdateAvailable) {
        public static ProjectReportStatus empty() { return new ProjectReportStatus(ReportStatus.NOT_STARTED, null, false); }
    }
}
