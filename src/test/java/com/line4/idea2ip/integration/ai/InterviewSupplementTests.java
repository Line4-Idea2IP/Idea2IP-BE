package com.line4.idea2ip.integration.ai;

import com.line4.idea2ip.domain.interview.entity.InterviewField;
import com.line4.idea2ip.domain.interview.entity.InterviewFieldState.Status;
import com.line4.idea2ip.integration.ai.AiInterviewClient.Answer;
import com.line4.idea2ip.integration.ai.AiInterviewClient.Context;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.line4.idea2ip.domain.interview.entity.InterviewField.*;
import static org.junit.jupiter.api.Assertions.*;

class InterviewSupplementTests {
    private final MockAiInterviewClient client = new MockAiInterviewClient();
    private final List<Answer> history = new ArrayList<>();

    private Context context() {
        return new Context(1L, "가상 작품", "snapshot", List.copyOf(history), List.of());
    }

    private Status answer(InterviewField field, String content) {
        var answer = new Answer((long) history.size() + 1, field, content);
        history.add(answer);
        return client.analyzeAnswer(context(), answer).status();
    }

    @Test void supplementOnlyWhenTheFirstSubstantiveAnswerMissesTheTopic() {
        assertEquals(Optional.of(InterviewSupplement.CREATIVE_CHOICE),
                InterviewSupplement.next(HUMAN_EDITED_EXPRESSION, List.of("시계판을 파란색으로 직접 칠했습니다.")));
        assertEquals(Optional.empty(),
                InterviewSupplement.next(HUMAN_EDITED_EXPRESSION, List.of("대비를 위해 시계판을 파란색으로 칠했습니다.")));
        assertEquals(Optional.empty(), InterviewSupplement.next(HUMAN_CREATED_ELEMENT, List.of("없습니다.")));
        assertEquals(Optional.empty(), InterviewSupplement.next(HUMAN_CREATED_ELEMENT, List.of("칠함")));
        assertEquals(Optional.empty(), InterviewSupplement.next(HUMAN_CREATED_ELEMENT, List.of("사슬을 그렸습니다.", "없습니다.")));
        assertEquals(Optional.of(InterviewSupplement.PRODUCTION_STAGE),
                InterviewSupplement.next(AI_OUTPUT_USAGE, List.of("손 모양을 그대로 가져왔습니다.")));
        assertEquals(Optional.empty(), InterviewSupplement.next(AI_OUTPUT_USAGE, List.of("손 배치 아이디어만 참고")));
        assertEquals(Optional.empty(), InterviewSupplement.next(AI_TOOL, List.of("Stable Diffusion")));
    }

    @Test void fieldStaysOpenForOneSupplementThenCompletes() {
        assertEquals(Status.NEEDS_REVIEW, answer(HUMAN_EDITED_EXPRESSION, "시계판을 파란색으로 직접 칠했습니다."));
        var question = client.generateQuestion(context(), HUMAN_EDITED_EXPRESSION);
        assertEquals(InterviewSupplement.CREATIVE_CHOICE.question, question.content());
        assertEquals(InterviewSupplement.CREATIVE_CHOICE.reason, question.reason());

        // Even "없습니다" answers the supplement; it never reopens or downgrades the field.
        var state = client.analyzeAnswer(context(), addAnswer(HUMAN_EDITED_EXPRESSION, "없습니다."));
        assertEquals(Status.COMPLETED, state.status());
        assertEquals("시계판을 파란색으로 직접 칠했습니다. / 없습니다.", state.summary());
    }

    @Test void answersThatAlreadyCoverTheTopicCompleteImmediately() {
        assertEquals(Status.COMPLETED, answer(HUMAN_CREATED_ELEMENT, "사슬을 손과 시계 사이에 배치해 흐름을 만들었습니다."));
        assertEquals(Status.COMPLETED, answer(AI_OUTPUT_USAGE, "구도 시안 단계에서 손 배치만 참고했습니다."));
        assertEquals(Status.NOT_APPLICABLE, answer(HUMAN_EDITED_EXPRESSION, "없습니다"));
    }

    @Test void shortAnswersKeepTheGenericFollowUp() {
        assertEquals(Status.NEEDS_REVIEW, answer(HUMAN_CREATED_ELEMENT, "칠함"));
        assertTrue(client.generateQuestion(context(), HUMAN_CREATED_ELEMENT).content().startsWith("앞선 답변을 조금 더 구체적으로"));
    }

    private Answer addAnswer(InterviewField field, String content) {
        var answer = new Answer((long) history.size() + 1, field, content);
        history.add(answer);
        return answer;
    }
}
