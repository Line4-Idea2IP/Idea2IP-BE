package com.line4.idea2ip.integration.ai;

import com.line4.idea2ip.domain.interview.entity.InterviewFieldState;
import com.line4.idea2ip.integration.ai.AiInterviewClient.Answer;
import com.line4.idea2ip.integration.ai.AiInterviewClient.AskedQuestion;
import com.line4.idea2ip.integration.ai.AiInterviewClient.Context;
import com.line4.idea2ip.integration.ai.AiInterviewClient.PreviousTurn;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.line4.idea2ip.domain.interview.entity.InterviewField.*;
import static com.line4.idea2ip.domain.interview.entity.InterviewFieldState.Status.COMPLETED;
import static com.line4.idea2ip.domain.interview.entity.InterviewFieldState.Status.MISSING;
import static org.junit.jupiter.api.Assertions.*;

class InterviewQuestionTemplatesTests {
    private static final String TOOL_QUESTION = MockAiInterviewClient.baseQuestion(AI_TOOL);

    private static Context afterTool(String answer, String toolSummary) {
        var fields = List.of(new InterviewFieldState(AI_TOOL, COMPLETED, toolSummary),
                new InterviewFieldState(AI_PURPOSE, MISSING, null));
        return new Context(1L, "작품", "s", List.of(new Answer(1L, AI_TOOL, answer, TOOL_QUESTION)), List.of(),
                List.of(new AskedQuestion(AI_TOOL, TOOL_QUESTION)), List.of(new PreviousTurn(AI_TOOL, TOOL_QUESTION, answer)),
                fields);
    }

    @Test void aiQuestionsNameTheToolAlreadyGiven() {
        var draft = InterviewQuestionTemplates.draft(afterTool("스테이블 디퓨젼 썼어요", "스테이블 디퓨젼"), AI_PURPOSE, null);

        // A known tool is named the usual way, whatever spelling the user wrote.
        assertEquals("Stable Diffusion 도구를 어떤 작업에, 어떤 목적으로 사용했나요?", draft.content());
        assertEquals("Gemini 도구를 어떤 작업에, 어떤 목적으로 사용했나요?",
                InterviewQuestionTemplates.draft(afterTool("제미나이로 했어요", "제미나이로"), AI_PURPOSE, null).content());
        assertEquals("우리팀툴 도구를 어떤 작업에, 어떤 목적으로 사용했나요?",
                InterviewQuestionTemplates.draft(afterTool("우리팀툴", "우리팀툴"), AI_PURPOSE, null).content());
    }

    @Test void aSentenceIsNeverSlottedInAsAToolName() {
        var draft = InterviewQuestionTemplates.draft(
                afterTool("그림 앱을 사용했습니다.", "그림 앱을 사용했습니다."), AI_PURPOSE, null);

        assertEquals(MockAiInterviewClient.baseQuestion(AI_PURPOSE), draft.content());
        assertFalse(InterviewQuestionTemplates.isToolName("Midjourney로 사과와 캔의 배치 시안을 만들었습니다."));
        assertTrue(InterviewQuestionTemplates.isToolName("Adobe Firefly"));
    }

    @Test void aReplyThatCouldNotBeQuotedIsConfirmedInTheUsersWords() {
        String answer = "하단 캔뚜껑 테두리를 회색애서 청회색으루 바궛고 흰색을 얇게 덧칠햇어요.";
        String question = MockAiInterviewClient.baseQuestion(HUMAN_EDITED_EXPRESSION);
        var context = new Context(1L, "작품", "s", List.of(new Answer(4L, HUMAN_EDITED_EXPRESSION, answer, question)), List.of(),
                List.of(new AskedQuestion(HUMAN_EDITED_EXPRESSION, question)),
                List.of(new PreviousTurn(HUMAN_EDITED_EXPRESSION, question, answer)));

        var draft = InterviewQuestionTemplates.draft(context, HUMAN_EDITED_EXPRESSION,
                InterviewQuestionTemplates.CONFIRM_CURRENT_ANSWER);

        assertEquals("‘하단 캔뚜껑 테두리를 회색애서 청회색으루 바궛고 흰색을 얇게 덧칠햇어요’라고 하셨는데, "
                + "직접 바꾼 부분이 무엇이고 어떻게 바꿨는지 알려주세요.", draft.content());
        assertEquals(InterviewQuestionTemplates.CONFIRMATION_REASON, draft.reason());
    }

    @Test void anEmbeddedQuestionClarificationIsAskedWithTheAnswerQuoted() {
        var context = new Context(1L, "작품", "s", List.of(new Answer(1L, AI_TOOL, "챗GPT는 안 썼어요", TOOL_QUESTION)), List.of(),
                List.of(new AskedQuestion(AI_TOOL, TOOL_QUESTION)), List.of(new PreviousTurn(AI_TOOL, TOOL_QUESTION, "챗GPT는 안 썼어요")));

        var draft = InterviewQuestionTemplates.draft(context, AI_TOOL, "다른 AI 도구를 사용했는지");

        assertEquals("‘챗GPT는 안 썼어요’라고 하셨는데, 다른 AI 도구를 사용했는지 알려주세요.", draft.content());
    }

    @Test void anUnreadableReplyIsNotQuotedBack() {
        String answer = "ㅁㄹ... ㅅㅈ ㅇㄱ? ㅠ";
        var context = new Context(1L, "작품", "s", List.of(new Answer(1L, AI_TOOL, answer, TOOL_QUESTION)), List.of(),
                List.of(new AskedQuestion(AI_TOOL, TOOL_QUESTION)), List.of(new PreviousTurn(AI_TOOL, TOOL_QUESTION, answer)));

        var draft = InterviewQuestionTemplates.draft(context, AI_TOOL, InterviewQuestionTemplates.CONFIRM_CURRENT_ANSWER);

        assertEquals("사용한 AI 도구의 이름을 알려주세요. 사용하지 않았다면 ‘없음’이라고 알려주세요.", draft.content());
    }

    @Test void anInstructionLikeClarificationFallsBackToAFieldQuestion() {
        var context = new Context(1L, "작품", "s", List.of(new Answer(1L, AI_TOOL, "네", TOOL_QUESTION)), List.of(),
                List.of(new AskedQuestion(AI_TOOL, TOOL_QUESTION)), List.of(new PreviousTurn(AI_TOOL, TOOL_QUESTION, "네")));

        var draft = InterviewQuestionTemplates.draft(context, AI_TOOL, "등록된 AI 도구와 답변의 차이를 확인하세요.");

        assertFalse(draft.content().contains("확인하세요"));
        assertFalse(InterviewQuestions.alreadyAsked(draft.content(), List.of(TOOL_QUESTION)));
    }

    @Test void aClarifyingQuestionAlreadyAskedIsNotRepeated() {
        String asked = "‘챗GPT는 안 썼어요’라고 하셨는데, 다른 AI 도구를 사용했는지 알려주세요.";
        var context = new Context(1L, "작품", "s", List.of(
                new Answer(1L, AI_TOOL, "챗GPT는 안 썼어요", TOOL_QUESTION),
                new Answer(3L, AI_TOOL, "챗GPT는 안 썼어요", asked)), List.of(),
                List.of(new AskedQuestion(AI_TOOL, TOOL_QUESTION), new AskedQuestion(AI_TOOL, asked)), List.of());

        var draft = InterviewQuestionTemplates.draft(context, AI_TOOL, "다른 AI 도구를 사용했는지");

        assertFalse(InterviewQuestions.alreadyAsked(draft.content(), List.of(TOOL_QUESTION, asked)));
    }
}
