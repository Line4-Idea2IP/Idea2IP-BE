package com.line4.idea2ip.domain.report;

import com.line4.idea2ip.domain.contribution.ContributionModels.*;
import com.line4.idea2ip.domain.report.ReportModels.*;
import com.line4.idea2ip.integration.ai.BriefAiClient.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ReportContentAssemblerTests {
    private final ReportContentAssembler assembler = new ReportContentAssembler();
    private final Input ai = new Input("test", "가상 작품", "v1", List.of());
    private final Draft draft = new Draft("1.0", "live", List.of(
            new Section("HUMAN_CONTRIBUTION", "모델 제목", List.of("제외한 원본 인터뷰"), List.of())), List.of());

    @Test void confirmedDecisionsReplaceModelInterviewAndPreserveOrder() {
        var confirmed = new Confirmed("사용자 수정 단계", new com.line4.idea2ip.domain.contribution.ContributionModels.Content(null, List.of("확정 창작"), List.of("확정 수정")), List.of());
        var result = assembler.assemble(new InputSnapshot(ai, List.of(confirmed), null, List.of()), draft, null, null);
        assertEquals(List.of("WORK_OVERVIEW", "AI_USAGE", "HUMAN_CONTRIBUTION", "EVIDENCE_SUMMARY", "GAPS_AND_GUIDANCE"),
                result.sections().stream().map(Section::sectionKey).toList());
        var human = result.sections().get(2);
        assertEquals("사용자가 확인한 창작 기여", human.title());
        assertEquals(List.of("사용자 수정 단계 · 직접 창작: 확정 창작", "사용자 수정 단계 · 직접 수정: 확정 수정"), human.paragraphs());
        assertTrue(result.sections().get(4).paragraphs().contains("검토된 공식 지침을 확인할 수 없습니다."));
    }

    @Test void noContributionReasonSupersedesPriorEditedContribution() {
        var before = new InputSnapshot(ai, List.of(), "기여 미확인", List.of());
        var base = assembler.assemble(before, draft, null, null);
        base = assembler.edit(base, List.of(new SectionEdit(SectionKey.HUMAN_CONTRIBUTION, List.of("이전 편집 기여"))));
        var after = new InputSnapshot(ai, List.of(), "사용자가 모든 기여를 제외함", List.of());
        var result = assembler.assemble(after, draft, base, before);
        assertEquals(List.of("사용자 확정: 창작 기여 없음 — 사용자가 모든 기여를 제외함"), result.sections().get(2).paragraphs());
        assertEquals(List.of("이전 편집 기여"), result.reviewFlags().getFirst().retainedUserParagraphs());
    }
}
