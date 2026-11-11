package com.line4.idea2ip.domain.contribution;

import com.line4.idea2ip.integration.ai.BriefAiClient.Candidate;
import com.line4.idea2ip.integration.ai.BriefAiClient.Input;
import com.line4.idea2ip.integration.ai.BriefAiClient.Source;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContributionProposalTests {
    private static Source answer(long id, String field, String targetField, String content) {
        String locator = "인터뷰 답변 " + id + " (" + field + ")";
        return new Source("answer:" + id, "USER_STATEMENT", locator, content, targetField, locator,
                null, null, id, null);
    }

    private static Input input(Source... sources) {
        return new Input("1", "가상 작품", "project-1-r1", List.of(sources));
    }

    @Test void declinedEditAnswerFromAiIsNotProposedAsEdit() {
        var source = answer(1, "HUMAN_EDITED_EXPRESSION", "human_edit", "없습니다.");
        var candidates = List.of(new Candidate("human_edit", "없습니다.", List.of(source.sourceId())));
        assertTrue(ContributionService.proposals(input(source), candidates).isEmpty());
    }

    @Test void concreteEditAnswerIsStillProposed() {
        var source = answer(1, "HUMAN_EDITED_EXPRESSION", "human_edit", "시계판을 직접 파란색으로 칠했습니다.");
        var candidates = List.of(new Candidate("human_edit", source.content(), List.of(source.sourceId())));
        assertEquals(1, ContributionService.proposals(input(source), candidates).size());
    }

    @Test void declinedCreationAnswerIsNotProposed() {
        var source = answer(2, "HUMAN_CREATED_ELEMENT", null, "해당 없음");
        assertTrue(ContributionService.proposals(input(source), List.of()).isEmpty());
    }
}
