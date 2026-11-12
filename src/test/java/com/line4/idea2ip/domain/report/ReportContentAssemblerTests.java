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
        assertEquals("인간의 창작적 표현 부분 (사용자 확인)", human.title());
        assertEquals(List.of("사용자 수정 단계 · 직접 창작: 확정 창작", "사용자 수정 단계 · 직접 수정: 확정 수정"), human.paragraphs());
        assertTrue(result.sections().get(4).paragraphs().contains(ReportContentAssembler.NO_OFFICIAL_GUIDE));
    }

    @Test void internalInterviewTitlesAreShownInPlainKorean() {
        var answer = new Evidence("answer:8", "USER_STATEMENT", "인터뷰 답변 8 (HUMAN_CREATED_ELEMENT)", "사슬을 구성했습니다.",
                null, null, null, null, null, null, null, null);
        var material = new Evidence("material:5", "ATTACHED_FILE", "구도 자료", null, null, null, null, null, null, null, null, null);
        var interviewOnly = new Confirmed("인터뷰 답변 8 (HUMAN_CREATED_ELEMENT)",
                new com.line4.idea2ip.domain.contribution.ContributionModels.Content(null, List.of("사슬을 구성했습니다."), List.of()),
                List.of(answer));
        var linked = new Confirmed("구도 자료",
                new com.line4.idea2ip.domain.contribution.ContributionModels.Content(null, List.of(), List.of("색을 바꿨습니다.")),
                List.of(material));
        var result = assembler.assemble(new InputSnapshot(ai, List.of(interviewOnly, linked), null, List.of()), draft, null, null);
        assertEquals(List.of("직접 창작: 사슬을 구성했습니다.", "구도 자료 · 직접 수정: 색을 바꿨습니다."),
                result.sections().get(2).paragraphs());
        assertEquals(List.of("사용자 진술 · 직접 창작한 부분", "첨부자료 · 구도 자료"), result.sections().get(3).paragraphs());
    }

    @Test void sectionsCiteOnlyAnswersBehindTheirStatements() {
        var material = new Evidence("material:5", "ATTACHED_FILE", "구도 자료", null, null, null, null, null, null, null, null, null);
        var tool = new Evidence("answer:1", "USER_STATEMENT", "답변 1", "Stable Diffusion", null, null, null, null, null, null, null, null);
        var edit = new Evidence("answer:2", "USER_STATEMENT", "답변 2", "시계판을 파란색으로 칠했습니다.", null, null, null, null, null, null, null, null);
        var content = new com.line4.idea2ip.domain.contribution.ContributionModels.Content(
                new AiUsage(List.of("Stable Diffusion"), null, null), List.of(), List.of("시계판을 파란색으로 칠했습니다."));
        var confirmed = new Confirmed("구도 자료", content, List.of(material, tool, edit));
        var result = assembler.assemble(new InputSnapshot(ai, List.of(confirmed), null, List.of()), draft, null, null);
        assertEquals(List.of("material:5", "answer:1"), result.sections().get(1).sourceIds());
        assertEquals(List.of("material:5", "answer:2"), result.sections().get(2).sourceIds());
    }

    @Test void editedStatementsFallBackToAllStepSources() {
        var material = new Evidence("material:5", "ATTACHED_FILE", "구도 자료", null, null, null, null, null, null, null, null, null);
        var edit = new Evidence("answer:2", "USER_STATEMENT", "답변 2", "원래 답변", null, null, null, null, null, null, null, null);
        var content = new com.line4.idea2ip.domain.contribution.ContributionModels.Content(null, List.of(), List.of("사용자가 고친 문장"));
        var confirmed = new Confirmed("구도 자료", content, List.of(material, edit));
        var result = assembler.assemble(new InputSnapshot(ai, List.of(confirmed), null, List.of()), draft, null, null);
        assertEquals(List.of("material:5", "answer:2"), result.sections().get(2).sourceIds());
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
