package com.line4.idea2ip.domain.interview.service;

import com.line4.idea2ip.domain.artwork.repository.FinalArtworkRepository;
import com.line4.idea2ip.domain.interview.dto.InterviewResponses.BlockingReason;
import com.line4.idea2ip.domain.interview.dto.InterviewResponses.MaterialCheck;
import com.line4.idea2ip.domain.interview.dto.InterviewResponses.MaterialRef;
import com.line4.idea2ip.domain.interview.dto.InterviewResponses.MaterialType;
import com.line4.idea2ip.domain.interview.dto.InterviewResponses.ReadinessRes;
import com.line4.idea2ip.domain.interview.entity.InterviewField;
import com.line4.idea2ip.domain.interview.entity.InterviewFieldState;
import com.line4.idea2ip.domain.material.entity.CreativeMaterial;
import com.line4.idea2ip.domain.material.entity.CreativeMaterialFile;
import com.line4.idea2ip.domain.material.repository.CreativeMaterialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

import static com.line4.idea2ip.domain.material.entity.MaterialType.AI_ASSISTED;
import static com.line4.idea2ip.domain.material.entity.MaterialType.MANUAL_EDIT;

@Service
@RequiredArgsConstructor
class InterviewReadinessService {
    private final FinalArtworkRepository artworkRepository;
    private final CreativeMaterialRepository materialRepository;

    List<CreativeMaterial> materials(Long projectId) {
        return materialRepository.findAllByProject_ProjectIdOrderByMaterialOrderAsc(projectId);
    }

    ReadinessRes assess(Long projectId, List<CreativeMaterial> materials, List<InterviewFieldState> states) {
        boolean artworkReady = artworkRepository.findByProject_ProjectId(projectId)
                .filter(artwork -> StringUtils.hasText(artwork.getImagePath()))
                .isPresent();
        boolean aiReady = materials.stream()
                .anyMatch(material -> material.getMaterialType() == AI_ASSISTED && hasFiles(material));
        boolean manualReady = materials.stream()
                .anyMatch(material -> material.getMaterialType() == MANUAL_EDIT && hasFiles(material));

        List<MaterialCheck> checks = List.of(
                new MaterialCheck(MaterialType.FINAL_ARTWORK, artworkReady),
                new MaterialCheck(MaterialType.AI_MATERIAL, aiReady),
                new MaterialCheck(MaterialType.MANUAL_EDIT_MATERIAL, manualReady)
        );
        List<BlockingReason> reasons = new ArrayList<>();
        if (!artworkReady) {
            reasons.add(new BlockingReason(MaterialType.FINAL_ARTWORK, "최종 작품 이미지를 등록해 주세요."));
        }
        if (!aiReady) {
            reasons.add(new BlockingReason(MaterialType.AI_MATERIAL, "AI 활용 자료와 이미지를 등록해 주세요."));
        }
        if (!manualReady) {
            reasons.add(new BlockingReason(MaterialType.MANUAL_EDIT_MATERIAL, "직접 수정 자료와 이미지를 등록해 주세요."));
        }
        return new ReadinessRes(reasons.isEmpty(), states, checks, List.copyOf(reasons));
    }

    List<InterviewFieldState> initialFields(List<CreativeMaterial> materials) {
        List<String> tools = materials.stream()
                .filter(InterviewReadinessService::hasFiles)
                .map(CreativeMaterial::getUsedAi)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (tools.isEmpty()) {
            return InterviewFieldState.initialStates();
        }
        String summary = "등록된 AI 도구: " + String.join(", ", tools);
        return InterviewFieldState.initialStates().stream()
                .map(state -> state.field() == InterviewField.AI_TOOL
                        ? new InterviewFieldState(state.field(), InterviewFieldState.Status.NEEDS_REVIEW, summary)
                        : state)
                .toList();
    }

    static boolean hasFiles(CreativeMaterial material) {
        return !material.getFiles().isEmpty();
    }

    static MaterialRef reference(CreativeMaterial material, CreativeMaterialFile file) {
        return new MaterialRef("material:" + material.getMaterialId() + ":file:" + file.getFileId(),
                material.getMaterialId(), file.getFileId(), material.getTitle());
    }
}
