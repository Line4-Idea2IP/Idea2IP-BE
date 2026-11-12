package com.line4.idea2ip.domain.report;

import com.line4.idea2ip.domain.contribution.ContributionModels.Confirmed;
import com.line4.idea2ip.domain.contribution.ContributionModels.Content;
import com.line4.idea2ip.domain.material.entity.MaterialType;
import com.line4.idea2ip.domain.report.ReportChecklist.MaterialInfo;
import com.line4.idea2ip.domain.report.ReportModels.InputSnapshot;
import com.line4.idea2ip.integration.ai.BriefAiClient.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReportChecklistTests {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 1);

    private static Evidence evidence(String id) {
        return new Evidence(id, id.startsWith("answer:") ? "USER_STATEMENT" : "ATTACHED_FILE", id, null, null,
                null, null, null, null, null, null, null);
    }

    @Test void completeProjectHasNothingToAdd() {
        var materials = List.of(new MaterialInfo("스케치", MaterialType.INITIAL_SKETCH, true, "초안", DAY),
                new MaterialInfo("AI 시안", MaterialType.AI_ASSISTED, true, "구도 비교", DAY),
                new MaterialInfo("수정본", MaterialType.MANUAL_EDIT, true, "색 수정", DAY));
        var linked = new Confirmed("수정본", new Content(null, List.of(), List.of("시계판을 칠했습니다.")),
                List.of(evidence("material:3"), evidence("answer:1")));
        assertEquals(List.of(ReportChecklist.NOTHING_MISSING),
                ReportChecklist.items(materials, true, "완성작 설명", List.of(linked), List.of()));
    }

    @Test void missingInputsBecomeSpecificActions() {
        var materials = List.of(new MaterialInfo("구도 참고 자료", MaterialType.AI_ASSISTED, true, null, null),
                new MaterialInfo("빈 자료", MaterialType.AI_ASSISTED, false, "설명", DAY));
        var unlinked = new Confirmed("인터뷰 답변 8", new Content(null, List.of("사슬의 흐름을 직접 구성했습니다."), List.of()),
                List.of(evidence("answer:8")));
        var items = ReportChecklist.items(materials, true, " ", List.of(unlinked),
                List.of("human_edit", "selection_arrangement"));

        assertTrue(items.stream().anyMatch(item -> item.startsWith("초기 스케치나 수정 전 시안 자료가 없습니다.")));
        assertTrue(items.stream().anyMatch(item -> item.startsWith("직접 수정 과정을 보여줄 자료가 없습니다.")));
        assertTrue(items.stream().noneMatch(item -> item.startsWith("AI로 만든")));
        assertTrue(items.contains("첨부 파일이 없는 자료: '빈 자료'. 각 자료에 해당 이미지를 첨부해 주세요."));
        assertTrue(items.contains("설명이 없는 자료: '구도 참고 자료'. 각 자료를 어느 단계에서 어떻게 사용했는지 적어 주세요."));
        assertTrue(items.contains("제작일이 없는 자료: '구도 참고 자료'. 제작 순서를 알 수 있도록 날짜를 입력해 주세요."));
        assertTrue(items.stream().anyMatch(item -> item.startsWith("최종 작품 설명이 없습니다.")));
        assertTrue(items.stream().anyMatch(item -> item.startsWith("확정한 기여 \"사슬의 흐름을 직접 구성했습니다.\"")));
        assertTrue(items.stream().anyMatch(item -> item.startsWith("직접 수정·추가한 부분에 관한 답변")));
        // Fields the backend interview never asks about cannot be resolved by the user, so they are not listed.
        assertTrue(items.stream().noneMatch(item -> item.contains("선택") && item.contains("배치")));
    }

    @Test void assemblerNumbersItemsAndKeepsGuidanceStatusLast() {
        var ai = new Input("1", "가상 작품", "v1", List.of());
        var draft = new Draft("1.0", "live", List.of(), List.of());
        var input = new InputSnapshot(ai, List.of(), null, List.of("첫째 항목", "둘째 항목"));
        var gaps = new ReportContentAssembler().assemble(input, draft, null, null).sections().get(4).paragraphs();
        assertEquals(List.of("1. 첫째 항목", "2. 둘째 항목", ReportContentAssembler.NO_OFFICIAL_GUIDE), gaps);

        var none = new InputSnapshot(ai, List.of(), null, List.of(ReportChecklist.NOTHING_MISSING));
        assertEquals(List.of(ReportChecklist.NOTHING_MISSING, ReportContentAssembler.NO_OFFICIAL_GUIDE),
                new ReportContentAssembler().assemble(none, draft, null, null).sections().get(4).paragraphs());
    }
}
