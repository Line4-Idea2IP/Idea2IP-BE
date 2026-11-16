package com.line4.idea2ip.integration.ai;

import com.line4.idea2ip.domain.artwork.entity.FinalArtwork;
import com.line4.idea2ip.domain.artwork.repository.FinalArtworkRepository;
import com.line4.idea2ip.domain.interview.entity.InterviewField;
import com.line4.idea2ip.domain.interview.entity.InterviewFieldState;
import com.line4.idea2ip.domain.interview.entity.InterviewFieldState.Status;
import com.line4.idea2ip.domain.interview.entity.InterviewMessage;
import com.line4.idea2ip.domain.interview.entity.InterviewMessage.Role;
import com.line4.idea2ip.domain.interview.repository.AiInterviewRepository;
import com.line4.idea2ip.domain.interview.repository.InterviewEvidenceRepository;
import com.line4.idea2ip.domain.interview.repository.InterviewMessageRepository;
import com.line4.idea2ip.domain.material.entity.CreativeMaterial;
import com.line4.idea2ip.domain.material.entity.MaterialType;
import com.line4.idea2ip.domain.material.repository.CreativeMaterialRepository;
import com.line4.idea2ip.domain.project.entity.Project;
import com.line4.idea2ip.integration.ai.BriefAiClient.Input;
import com.line4.idea2ip.integration.ai.BriefAiClient.Source;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ProjectBriefInput {
    private final AiInterviewRepository interviews;
    private final InterviewMessageRepository messages;
    private final CreativeMaterialRepository materials;
    private final FinalArtworkRepository artworks;
    private final InterviewEvidenceRepository evidence;

    public Input capture(Project project) {
        Long projectId = project.getProjectId();
        List<Source> sources = new ArrayList<>();
        var artwork = artworks.findByProject_ProjectId(projectId).orElse(null);
        if (artwork != null) {
            sources.add(new Source("artwork:" + artwork.getArtworkId(), "ATTACHED_FILE", artwork.getTitle(),
                    describe(artwork), null, artwork.getOriginalFileName(), null, artwork.getArtworkId(), null, artwork.getImagePath()));
        }
        Map<Long, MaterialType> materialTypes = new HashMap<>();
        for (var material : materials.findAllByProject_ProjectIdOrderByMaterialOrderAsc(projectId)) {
            materialTypes.put(material.getMaterialId(), material.getMaterialType());
            sources.add(new Source("material:" + material.getMaterialId(), "ATTACHED_FILE", material.getTitle(),
                    describe(material), null, null, material.getMaterialId(), null, null,
                    material.getFiles().stream().map(file -> file.getFileId().toString()).sorted()
                            .collect(java.util.stream.Collectors.joining(","))));
        }
        interviews.findByProject_ProjectId(projectId).ifPresent(interview -> {
            var history = messages.findAllByInterview_InterviewIdOrderBySequenceNo(interview.getInterviewId());
            for (var message : history) {
                if (message.getRole() != Role.USER) {
                    continue;
                }
                String quote = message.getValidatedEvidenceQuote();
                if ("".equals(quote)) continue;
                String locator = "인터뷰 답변 " + message.getSequenceNo() + " (" + message.getTargetField() + ")";
                Long materialId = linkedMaterial(message.getReplyToQuestion().getSourceIds(),
                        message.getTargetField(), materialTypes);
                sources.add(new Source("answer:" + message.getMessageId(), "USER_STATEMENT", locator,
                        quote == null ? message.getContent() : quote, field(message.getTargetField()), locator, materialId, null,
                        message.getMessageId(), null, quote == null ? null : true));
            }
            var answers = history.stream().filter(message -> message.getRole() == Role.USER)
                    .collect(Collectors.toMap(InterviewMessage::getMessageId, message -> message));
            var ledger = evidence.findAllByProject_ProjectIdOrderByEvidenceIdAsc(projectId);
            if (ledger.stream().noneMatch(row -> row.getMessageId() != null)) {
                legacySettledSources(interview.getFieldStates(), history, materialTypes, sources);
                return;
            }
            // A sentence that answered a field it was not asked about is cited again under that field.
            for (var row : ledger) {
                var message = row.getMessageId() == null ? null : answers.get(row.getMessageId());
                if (message == null || row.getField() == message.getTargetField()) continue;
                sources.add(settledSource(message, row.getField(), row.getQuote(), materialTypes));
            }
        });
        return new Input(projectId.toString(), project.getTitle(), "project-" + projectId + "-r" + project.getInputRevision(),
                List.copyOf(sources));
    }

    /**
     * Interviews settled before the evidence ledger existed: a completed field without an answer of its own is
     * cited under the earlier answer that contains its summary.
     */
    private void legacySettledSources(List<InterviewFieldState> states, List<InterviewMessage> history,
                                      Map<Long, MaterialType> materialTypes, List<Source> sources) {
        var answeredFields = history.stream().filter(message -> message.getRole() == Role.USER)
                .map(InterviewMessage::getTargetField).collect(Collectors.toSet());
        for (var state : states) {
            if (state.status() != Status.COMPLETED || state.summary() == null || state.summary().isBlank()
                    || answeredFields.contains(state.field())) continue;
            history.stream().filter(message -> message.getRole() == Role.USER && message.getContent().contains(state.summary()))
                    .findFirst()
                    .ifPresent(message -> sources.add(settledSource(message, state.field(), state.summary(), materialTypes)));
        }
    }

    private Source settledSource(InterviewMessage message, InterviewField field, String quote,
                                 Map<Long, MaterialType> materialTypes) {
        String locator = "인터뷰 답변 " + message.getSequenceNo() + " (" + field + ")";
        Long materialId = linkedMaterial(message.getReplyToQuestion().getSourceIds(), field, materialTypes);
        return new Source("answer:" + message.getMessageId() + ":" + field, "USER_STATEMENT", locator, quote,
                field(field), locator, materialId, null, message.getMessageId(), null, true);
    }

    /**
     * Links an answer to one material cited by its question. Questions often cite every material, so
     * the material whose type matches the field wins; otherwise only an unambiguous single citation links.
     */
    static Long linkedMaterial(List<String> questionSourceIds, InterviewField field, Map<Long, MaterialType> types) {
        List<Long> cited = questionSourceIds.stream()
                .map(id -> id.split(":"))
                .filter(parts -> parts.length == 4 && parts[0].equals("material") && parts[2].equals("file"))
                .map(parts -> Long.valueOf(parts[1]))
                .filter(types::containsKey)
                .distinct().toList();
        MaterialType expected = MockAiInterviewClient.referenceType(field);
        List<Long> matching = cited.stream().filter(id -> types.get(id) == expected).toList();
        if (matching.size() == 1) return matching.getFirst();
        return matching.isEmpty() && cited.size() == 1 ? cited.getFirst() : null;
    }

    private String field(InterviewField field) {
        return switch (field) {
            case AI_TOOL, AI_PURPOSE, AI_OUTPUT_USAGE -> "ai_usage";
            case HUMAN_EDITED_EXPRESSION -> "human_edit";
            case HUMAN_CREATED_ELEMENT -> null;
        };
    }

    private String describe(CreativeMaterial material) {
        return material.getTitle() + " (" + material.getMaterialType() + ")"
                + (material.getDescription() == null ? "" : "\n" + material.getDescription())
                + (material.getUsedAi() == null ? "" : "\n사용 도구: " + material.getUsedAi());
    }

    private String describe(FinalArtwork artwork) {
        return artwork.getTitle() + (artwork.getDescription() == null ? "" : "\n" + artwork.getDescription());
    }
}
