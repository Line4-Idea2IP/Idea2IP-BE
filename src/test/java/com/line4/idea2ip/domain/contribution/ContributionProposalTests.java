package com.line4.idea2ip.domain.contribution;

import com.line4.idea2ip.domain.contribution.ContributionModels.Content;
import com.line4.idea2ip.domain.contribution.ContributionModels.Decision;
import com.line4.idea2ip.domain.contribution.ContributionModels.Kind;
import com.line4.idea2ip.domain.contribution.ContributionService.Key;
import com.line4.idea2ip.integration.ai.BriefAiClient.Candidate;
import com.line4.idea2ip.integration.ai.BriefAiClient.Evidence;
import com.line4.idea2ip.integration.ai.BriefAiClient.Input;
import com.line4.idea2ip.integration.ai.BriefAiClient.Source;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ContributionProposalTests {
    private static Source answer(long id, String field, String targetField, String content, Long materialId) {
        String locator = "인터뷰 답변 " + id + " (" + field + ")";
        return new Source("answer:" + id, "USER_STATEMENT", locator, content, targetField, locator,
                materialId, null, id, null);
    }

    private static Source answer(long id, String field, String targetField, String content) {
        return answer(id, field, targetField, content, null);
    }

    private static Source material(long id, String title) {
        return new Source("material:" + id, "ATTACHED_FILE", title, title, null, null, id, null, null, null);
    }

    private static Input input(Source... sources) {
        return new Input("1", "가상 작품", "project-1-r1", List.of(sources));
    }

    private static Map<Key, ?> proposals(Input input, List<Candidate> candidates) {
        return ContributionService.proposals(input, candidates, Map.of());
    }

    @Test void declinedEditAnswerFromAiIsNotProposedAsEdit() {
        var source = answer(1, "HUMAN_EDITED_EXPRESSION", "human_edit", "없습니다.");
        var candidates = List.of(new Candidate("human_edit", "없습니다.", List.of(source.sourceId())));
        assertTrue(proposals(input(source), candidates).isEmpty());
    }

    @Test void concreteEditAnswerIsStillProposed() {
        var source = answer(1, "HUMAN_EDITED_EXPRESSION", "human_edit", "시계판을 직접 파란색으로 칠했습니다.");
        var candidates = List.of(new Candidate("human_edit", source.content(), List.of(source.sourceId())));
        assertEquals(1, proposals(input(source), candidates).size());
    }

    @Test void declinedCreationAnswerIsNotProposed() {
        var source = answer(2, "HUMAN_CREATED_ELEMENT", null, "해당 없음");
        assertTrue(proposals(input(source), List.of()).isEmpty());
    }

    @Test void reviewedAnswerStaysOnItsDecidedStepWhenMaterialLinkChanges() {
        // The user excluded this edit while it was linked to material 5; a regenerated input links it to material 6.
        var oldStep = new ContributionStep(null, Kind.MATERIAL, 5L, "구도 참고 자료");
        var edit = answer(30, "HUMAN_EDITED_EXPRESSION", "human_edit", "시계판을 파란색으로 직접 칠했습니다.", 6L);
        oldStep.propose(new Content(null, List.of(), List.of(edit.content())),
                List.of(Evidence.from(edit, edit.content())));
        oldStep.apply(Decision.EXCLUDE, null);
        var unreviewed = new ContributionStep(null, Kind.MATERIAL, 7L, "검토 전 자료");
        unreviewed.propose(new Content(null, List.of(), List.of("다른 답변")),
                List.of(Evidence.from(answer(31, "HUMAN_EDITED_EXPRESSION", "human_edit", "다른 답변"), "다른 답변")));

        var pinned = ContributionService.reviewedAnswers(List.of(oldStep, unreviewed));
        assertEquals(Map.of("answer:30", new Key(Kind.MATERIAL, 5L)), pinned);

        var candidates = List.of(new Candidate("human_edit", edit.content(), List.of(edit.sourceId())));
        var result = ContributionService.proposals(
                input(material(5, "구도 참고 자료"), material(6, "색 대비 자료"), edit), candidates, pinned);
        assertEquals(java.util.Set.of(new Key(Kind.MATERIAL, 5L)), result.keySet());
    }
}
