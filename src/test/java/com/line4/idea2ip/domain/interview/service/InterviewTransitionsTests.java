package com.line4.idea2ip.domain.interview.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.line4.idea2ip.domain.interview.entity.InterviewField.*;
import static org.junit.jupiter.api.Assertions.*;

class InterviewTransitionsTests {
    @Test void fieldsSettledByAnAnswerAreNamedBeforeMovingToOwnWork() {
        assertEquals("말씀하신 내용으로 AI 사용 목적, 최종 작품 활용 여부까지 확인했습니다. 이제 직접 작업하신 부분을 여쭤볼게요. ",
                InterviewTransitions.leadIn(AI_TOOL, HUMAN_CREATED_ELEMENT, List.of(AI_PURPOSE, AI_OUTPUT_USAGE), false));
    }

    @Test void askingTheSameFieldAgainSaysSo() {
        assertEquals("조금만 더 여쭤볼게요. ", InterviewTransitions.leadIn(AI_TOOL, AI_TOOL, List.of(), false));
    }

    @Test void movingWithinAiFieldsNeedsNoLeadIn() {
        assertEquals("", InterviewTransitions.leadIn(AI_TOOL, AI_PURPOSE, List.of(), false));
        assertEquals("", InterviewTransitions.leadIn(HUMAN_CREATED_ELEMENT, HUMAN_EDITED_EXPRESSION, List.of(), false));
    }

    @Test void theFirstQuestionNamesWhatTheDescriptionSettled() {
        assertEquals("작품 설명에서 사용한 AI 도구, AI 사용 목적까지 확인했습니다. ",
                InterviewTransitions.leadIn(null, AI_OUTPUT_USAGE, List.of(AI_TOOL, AI_PURPOSE), true));
        assertEquals("", InterviewTransitions.leadIn(null, AI_TOOL, List.of(), true));
    }

    @Test void theBodyIsTheQuestionWithoutItsLeadIn() {
        String question = "직접 새로 만든 요소가 있다면 무엇이고, 어떤 방식으로 만들었나요?";
        String leadIn = InterviewTransitions.leadIn(AI_TOOL, HUMAN_CREATED_ELEMENT, List.of(AI_PURPOSE), false);

        assertEquals(question, InterviewTransitions.body(InterviewTransitions.withLeadIn(leadIn, question)));
        assertEquals(question, InterviewTransitions.body("조금만 더 여쭤볼게요. " + question));
        assertEquals(question, InterviewTransitions.body(question));
    }

    @Test void aLeadInThatWouldOverflowTheQuestionIsDropped() {
        String question = "가".repeat(495);

        assertEquals(question, InterviewTransitions.withLeadIn("조금만 더 여쭤볼게요. ", question));
    }
}
