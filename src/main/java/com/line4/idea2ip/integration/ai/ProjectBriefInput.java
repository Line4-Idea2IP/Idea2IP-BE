package com.line4.idea2ip.integration.ai;

import com.line4.idea2ip.domain.artwork.entity.FinalArtwork;
import com.line4.idea2ip.domain.artwork.repository.FinalArtworkRepository;
import com.line4.idea2ip.domain.interview.entity.InterviewField;
import com.line4.idea2ip.domain.interview.entity.InterviewMessage.Role;
import com.line4.idea2ip.domain.interview.repository.AiInterviewRepository;
import com.line4.idea2ip.domain.interview.repository.InterviewMessageRepository;
import com.line4.idea2ip.domain.material.entity.CreativeMaterial;
import com.line4.idea2ip.domain.material.repository.CreativeMaterialRepository;
import com.line4.idea2ip.domain.project.entity.Project;
import com.line4.idea2ip.integration.ai.BriefAiClient.Input;
import com.line4.idea2ip.integration.ai.BriefAiClient.Source;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ProjectBriefInput {
    private final AiInterviewRepository interviews;
    private final InterviewMessageRepository messages;
    private final CreativeMaterialRepository materials;
    private final FinalArtworkRepository artworks;

    public Input capture(Project project) {
        Long projectId = project.getProjectId();
        List<Source> sources = new ArrayList<>();
        var artwork = artworks.findByProject_ProjectId(projectId).orElse(null);
        if (artwork != null) {
            sources.add(new Source("artwork:" + artwork.getArtworkId(), "ATTACHED_FILE", artwork.getTitle(),
                    describe(artwork), null, artwork.getOriginalFileName(), null, artwork.getArtworkId(), null, artwork.getImagePath()));
        }
        for (var material : materials.findAllByProject_ProjectIdOrderByMaterialOrderAsc(projectId)) {
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
                String locator = "인터뷰 답변 " + message.getSequenceNo() + " (" + message.getTargetField() + ")";
                Long materialId = message.getReplyToQuestion().getSourceIds().stream()
                        .map(id -> id.split(":"))
                        .filter(parts -> parts.length == 4 && parts[0].equals("material") && parts[2].equals("file"))
                        .map(parts -> Long.valueOf(parts[1]))
                        .filter(id -> sources.stream().anyMatch(source -> id.equals(source.materialId())))
                        .findFirst().orElse(null);
                sources.add(new Source("answer:" + message.getMessageId(), "USER_STATEMENT", locator,
                        message.getContent(), field(message.getTargetField()), locator, materialId, null,
                        message.getMessageId(), null));
            }
        });
        return new Input(projectId.toString(), project.getTitle(), "project-" + projectId + "-r" + project.getInputRevision(),
                List.copyOf(sources));
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
