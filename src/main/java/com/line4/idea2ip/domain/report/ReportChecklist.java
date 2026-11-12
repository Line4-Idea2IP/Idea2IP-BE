package com.line4.idea2ip.domain.report;

import com.line4.idea2ip.domain.artwork.repository.FinalArtworkRepository;
import com.line4.idea2ip.domain.contribution.ContributionModels.Confirmed;
import com.line4.idea2ip.domain.material.entity.MaterialType;
import com.line4.idea2ip.domain.material.repository.CreativeMaterialRepository;
import com.line4.idea2ip.domain.project.entity.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Rule-based "what to add next" items for the report's gaps section. Built only from stored project data,
 * never from model text, so every item names a concrete missing input and the action that fills it.
 */
@Component
@RequiredArgsConstructor
class ReportChecklist {
    static final String NOTHING_MISSING = "추가로 보완할 자료나 설명이 확인되지 않았습니다.";

    private final CreativeMaterialRepository materials;
    private final FinalArtworkRepository artworks;

    record MaterialInfo(String title, MaterialType type, boolean hasFiles, String description, LocalDate productionDate) {}

    List<String> items(Project project, List<Confirmed> confirmed, List<String> unresolvedFields) {
        Long projectId = project.getProjectId();
        var info = materials.findAllByProject_ProjectIdOrderByMaterialOrderAsc(projectId).stream()
                .map(material -> new MaterialInfo(material.getTitle(), material.getMaterialType(),
                        !material.getFiles().isEmpty(), material.getDescription(), material.getProductionDate()))
                .toList();
        var artwork = artworks.findByProject_ProjectId(projectId).orElse(null);
        return items(info, artwork != null, artwork == null ? null : artwork.getDescription(), confirmed, unresolvedFields);
    }

    static List<String> items(List<MaterialInfo> materials, boolean hasArtwork, String artworkDescription,
                              List<Confirmed> confirmed, List<String> unresolvedFields) {
        List<String> items = new ArrayList<>();
        missingType(items, materials, MaterialType.INITIAL_SKETCH,
                "초기 스케치나 수정 전 시안 자료가 없습니다. 직접 작업의 출발점을 보여줄 자료가 있다면 첨부해 주세요.");
        missingType(items, materials, MaterialType.AI_ASSISTED,
                "AI로 만든 시안이나 결과물 자료가 없습니다. AI를 사용했다면 해당 산출물을 첨부해 주세요.");
        missingType(items, materials, MaterialType.MANUAL_EDIT,
                "직접 수정 과정을 보여줄 자료가 없습니다. 수정 전후를 비교할 수 있는 중간 수정본을 첨부해 주세요.");
        listed(items, materials, material -> !material.hasFiles(), "첨부 파일이 없는 자료",
                "각 자료에 해당 이미지를 첨부해 주세요.");
        listed(items, materials, material -> !StringUtils.hasText(material.description()), "설명이 없는 자료",
                "각 자료를 어느 단계에서 어떻게 사용했는지 적어 주세요.");
        listed(items, materials, material -> material.productionDate() == null, "제작일이 없는 자료",
                "제작 순서를 알 수 있도록 날짜를 입력해 주세요.");
        if (hasArtwork && !StringUtils.hasText(artworkDescription)) {
            items.add("최종 작품 설명이 없습니다. 완성작의 구성과 표현하려는 내용을 적어 주세요.");
        }
        for (var step : confirmed) {
            boolean linked = step.sources().stream().anyMatch(source -> source.sourceId().startsWith("material:")
                    || source.sourceId().startsWith("artwork:"));
            if (linked) continue;
            var statements = new ArrayList<>(step.content().humanCreatedElements());
            statements.addAll(step.content().humanEdits());
            if (statements.isEmpty()) continue;
            items.add("확정한 기여 \"" + excerpt(statements.getFirst()) + "\"를 뒷받침하는 제작 자료가 연결되지 않았습니다. "
                    + "이 작업을 보여주는 자료가 있다면 첨부하고, 자료 설명에 해당 작업을 적어 주세요.");
        }
        if (unresolvedFields.contains("ai_usage")) {
            items.add("AI 활용 내역에 관한 답변이 모호하거나 확인되지 않았습니다. 사용한 도구와 활용 방식을 기여 검토에서 확인해 주세요.");
        }
        if (unresolvedFields.contains("human_edit")) {
            items.add("직접 수정·추가한 부분에 관한 답변이 모호하거나 확인되지 않았습니다. 작업한 요소와 방법을 기여 검토에서 확인해 주세요.");
        }
        return items.isEmpty() ? List.of(NOTHING_MISSING) : List.copyOf(items);
    }

    private static void missingType(List<String> items, List<MaterialInfo> materials, MaterialType type, String message) {
        if (materials.stream().noneMatch(material -> material.type() == type && material.hasFiles())) items.add(message);
    }

    private static void listed(List<String> items, List<MaterialInfo> materials, Predicate<MaterialInfo> missing,
                               String label, String action) {
        var titles = materials.stream().filter(missing).map(material -> "'" + excerpt(material.title()) + "'").toList();
        if (!titles.isEmpty()) items.add(label + ": " + String.join(", ", titles) + ". " + action);
    }

    private static String excerpt(String value) {
        String text = value.strip().replaceAll("\\s+", " ");
        int max = 40;
        if (text.codePointCount(0, text.length()) <= max) return text;
        return text.substring(0, text.offsetByCodePoints(0, max)) + "…";
    }
}
