package com.line4.idea2ip.domain.contribution;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.line4.idea2ip.global.job.PersistentJob;
import com.line4.idea2ip.integration.ai.BriefAiClient.Evidence;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class ContributionModels {
    private ContributionModels() {}

    public enum Kind { MATERIAL, FINAL_ARTWORK, INTERVIEW_STATEMENT }
    public enum ReviewStatus { NOT_STARTED, IN_REVIEW, CONFIRMED }
    public enum StepStatus { UNREVIEWED, EDITED, CONFIRMED, EXCLUDED }
    public enum Decision { EDIT, EXCLUDE, CONFIRM }

    public record AiUsage(@Size(max = 100) List<@NotBlank @Size(max = 200) String> tools,
                          @Size(max = 8000) String purpose, @Size(max = 8000) String outputUsage) {
        public AiUsage apply(AiUsage edit) {
            if (edit == null) return this;
            return new AiUsage(edit.tools == null ? tools : edit.tools,
                    edit.purpose == null ? purpose : edit.purpose,
                    edit.outputUsage == null ? outputUsage : edit.outputUsage);
        }
    }
    public record Content(@Valid AiUsage aiUsage,
                          @Size(max = 100) List<@NotBlank @Size(max = 8000) String> humanCreatedElements,
                          @Size(max = 100) List<@NotBlank @Size(max = 8000) String> humanEdits) {
        public static Content empty() { return new Content(null, List.of(), List.of()); }

        public Content apply(Content edit) {
            return edit == null ? this : new Content(aiUsage == null ? edit.aiUsage : aiUsage.apply(edit.aiUsage),
                    edit.humanCreatedElements == null ? humanCreatedElements : edit.humanCreatedElements,
                    edit.humanEdits == null ? humanEdits : edit.humanEdits);
        }

        public boolean hasContribution() {
            return aiUsage != null && ((aiUsage.tools() != null && !aiUsage.tools().isEmpty())
                    || org.springframework.util.StringUtils.hasText(aiUsage.purpose())
                    || org.springframework.util.StringUtils.hasText(aiUsage.outputUsage()))
                    || !humanCreatedElements.isEmpty() || !humanEdits.isEmpty();
        }
    }

    public record GenerateReq(@NotNull UUID requestId, @NotNull @PositiveOrZero Long expectedRevision, Boolean retry) {
        public boolean retryRequested() { return Boolean.TRUE.equals(retry); }
    }
    public record Change(@NotNull Long stepId, @NotNull Decision decision, @Valid Content edits) {}
    public record PatchReq(@NotNull @PositiveOrZero Long expectedRevision,
                           @NotNull @Size(max = 500) List<@NotNull @Valid Change> changes,
                           @JsonProperty("finalize") Boolean finalizeReview, @Size(max = 2000) String noContributionReason) {
        public boolean finalizeRequested() { return Boolean.TRUE.equals(finalizeReview); }
    }

    public record GenerationView(Long generationId, UUID requestId, PersistentJob.Status status, PersistentJob.Failure failure) {}
    /**
     * productionDate: date the user entered for the material (null when not given or not a material).
     * recordedAt: server time — latest file upload for a material, current image upload for the artwork, answer
     *             time for an interview statement. Files stored before upload times existed fall back to registration.
     */
    public record Summary(Long stepId, int order, Kind kind, String title, StepStatus reviewStatus,
                          LocalDate productionDate, LocalDateTime recordedAt) {}
    public record View(ReviewStatus reviewStatus, long revision, List<Summary> timeline,
                       List<String> unresolvedFields, GenerationView latestGeneration) {}
    public record FileRef(Long fileId, String originalFileName, String fileUrl) {}
    public record MaterialRef(Long materialId, String title, List<FileRef> files) {}
    public record ArtworkRef(Long artworkId, String title, String fileUrl) {}
    public record Detail(Long stepId, int order, Kind kind, String title, StepStatus reviewStatus,
                         List<MaterialRef> materialRefs, ArtworkRef finalArtworkRef, AiUsage aiUsage,
                         List<String> humanCreatedElements, List<String> humanEdits, List<Evidence> sources,
                         LocalDate productionDate, LocalDateTime recordedAt) {}
    public record Confirmed(String title, Content content, List<Evidence> sources) {}
}
