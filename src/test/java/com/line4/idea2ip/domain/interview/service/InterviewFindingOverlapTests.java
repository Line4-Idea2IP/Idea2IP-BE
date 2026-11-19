package com.line4.idea2ip.domain.interview.service;

import com.line4.idea2ip.integration.ai.AiInterviewClient.Answer;
import org.junit.jupiter.api.Test;

import static com.line4.idea2ip.domain.interview.entity.InterviewField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Deployed: a creation answer was also taken as the edit field's evidence, so edits were never asked. */
class InterviewFindingOverlapTests {
    private static final String CREATION = "고양이와 해바라기를 직접 그렸습니다. 연필로 윤곽을 잡고 수채 브러시로 칠했습니다.";

    @Test void aFindingCannotTakeAllOfTheAskedFieldsEvidence() {
        var answer = new Answer(1L, HUMAN_CREATED_ELEMENT, CREATION, "직접 만든 요소는?", CREATION);

        assertTrue(InterviewService.takesAskedEvidence(CREATION, answer));
        assertTrue(InterviewService.takesAskedEvidence(CREATION, new Answer(1L, HUMAN_CREATED_ELEMENT, CREATION, "q",
                "고양이와 해바라기를 직접 그렸습니다.")), "the finding holds the whole narrower quote too");
    }

    @Test void aFindingMayTakeOtherSentencesOfTheAnswer() {
        String answerText = "사과 단면을 직접 그렸습니다. 캔 뚜껑 테두리는 청회색으로 바꿨습니다.";
        var answer = new Answer(1L, HUMAN_CREATED_ELEMENT, answerText, "q", answerText);

        assertFalse(InterviewService.takesAskedEvidence("캔 뚜껑 테두리는 청회색으로 바꿨습니다.", answer),
                "the asked field keeps its own sentence once its quote is narrowed");
    }

    @Test void theToolQuoteAndUnquotedAnswersAreExempt() {
        var tool = new Answer(1L, AI_TOOL, "Midjourney로 배치를 비교했어요", "q", "Midjourney");
        assertFalse(InterviewService.takesAskedEvidence("Midjourney로 배치를 비교했어요", tool));

        var noQuote = new Answer(1L, HUMAN_EDITED_EXPRESSION, "없습니다", "q", "");
        assertFalse(InterviewService.takesAskedEvidence("없습니다", noQuote));
        assertFalse(InterviewService.takesAskedEvidence("문장", new Answer(1L, AI_PURPOSE, "문장", "q", null)));
    }
}
