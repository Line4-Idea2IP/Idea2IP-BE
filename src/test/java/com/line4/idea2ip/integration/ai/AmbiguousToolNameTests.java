package com.line4.idea2ip.integration.ai;

import com.line4.idea2ip.domain.interview.entity.InterviewFieldState;
import com.line4.idea2ip.integration.ai.AiInterviewClient.Answer;
import com.line4.idea2ip.integration.ai.AiInterviewClient.AnswerAnalysis;
import com.line4.idea2ip.integration.ai.AiInterviewClient.AskedQuestion;
import com.line4.idea2ip.integration.ai.AiInterviewClient.Context;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.line4.idea2ip.domain.interview.entity.InterviewField.AI_TOOL;
import static com.line4.idea2ip.domain.interview.entity.InterviewFieldState.Status.COMPLETED;
import static com.line4.idea2ip.domain.interview.entity.InterviewFieldState.Status.NEEDS_REVIEW;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Real model: "소라", "달리", "빙", "바드" were taken as written, and "달리로 그렸어요" got the generic tool question.
 * An everyday word given as the tool is confirmed first: "‘소라’라고 하셨는데, OpenAI의 Sora를 말씀하신 게 맞나요?".
 */
class AmbiguousToolNameTests {
    private static final String TOOL_QUESTION = MockAiInterviewClient.baseQuestion(AI_TOOL);
    private static final String SORA_QUESTION = "‘소라’라고 하셨는데, OpenAI의 Sora를 말씀하신 게 맞나요? "
            + "맞다면 ‘네’, 아니라면 사용한 도구 이름을 알려주세요.";

    private static AnswerAnalysis completed(String quote) {
        return new AnswerAnalysis(new InterviewFieldState(AI_TOOL, COMPLETED, quote), quote, null);
    }

    private static AnswerAnalysis unresolved(String clarification) {
        return new AnswerAnalysis(new InterviewFieldState(AI_TOOL, NEEDS_REVIEW, "확인"), "", clarification);
    }

    @Test void anEverydayWordTakenAsTheToolIsConfirmedFirst() {
        var analysis = LlamaInterviewClient.acceptBareToolName(new Answer(1L, AI_TOOL, "소라", TOOL_QUESTION), completed("소라"));

        assertEquals(NEEDS_REVIEW, analysis.state().status());
        assertEquals("", analysis.evidenceQuote(), "the everyday word is not cited before it is confirmed");
        assertEquals(SORA_QUESTION, analysis.clarification());
        assertFalse(analysis.confirmedName());
    }

    @Test void eachEverydayWordNamesItsTool() {
        assertTrue(LlamaInterviewClient.acceptBareToolName(new Answer(1L, AI_TOOL, "달리로 그렸어요", TOOL_QUESTION),
                unresolved("'달리'가 AI 도구인지 확인할 필요가 있습니다.")).clarification().contains("OpenAI의 DALL·E를"));
        assertTrue(LlamaInterviewClient.acceptBareToolName(new Answer(1L, AI_TOOL, "빙", TOOL_QUESTION), completed("빙"))
                .clarification().contains("Microsoft의 Bing Image Creator를"));
        assertTrue(LlamaInterviewClient.acceptBareToolName(new Answer(1L, AI_TOOL, "바드 썼어요", TOOL_QUESTION), completed("바드"))
                .clarification().contains("Google의 Bard(현 Gemini)를"));
        assertTrue(LlamaInterviewClient.acceptBareToolName(new Answer(1L, AI_TOOL, "소라로 영상 만들었어요", TOOL_QUESTION),
                completed("소라")).clarification().startsWith("‘소라’라고 하셨는데"));
    }

    @Test void aKnownToolOrADenialIsNotConfirmed() {
        var known = LlamaInterviewClient.acceptBareToolName(new Answer(1L, AI_TOOL, "Sora로 영상 만들었어요", TOOL_QUESTION),
                completed("Sora"));
        assertEquals(COMPLETED, known.state().status());
        var both = LlamaInterviewClient.acceptBareToolName(new Answer(1L, AI_TOOL, "미드저니랑 소라", TOOL_QUESTION),
                completed("미드저니"));
        assertEquals(COMPLETED, both.state().status());
        var denied = LlamaInterviewClient.acceptBareToolName(new Answer(1L, AI_TOOL, "소라는 안 썼어요", TOOL_QUESTION),
                unresolved("어떤 도구인가요?"));
        assertFalse(denied.clarification() != null && denied.clarification().contains("맞나요"));
    }

    @Test void theConfirmationQuestionIsAskedAsWritten() {
        var context = new Context(1L, "작품", "s", List.of(new Answer(1L, AI_TOOL, "소라", TOOL_QUESTION)), List.of(),
                List.of(new AskedQuestion(AI_TOOL, TOOL_QUESTION)), List.of());

        var draft = InterviewQuestionTemplates.draft(context, AI_TOOL, SORA_QUESTION);

        assertEquals(SORA_QUESTION, draft.content());
        assertEquals(InterviewQuestionTemplates.TOOL_CONFIRMATION_REASON, draft.reason());
    }

    @Test void yesSettlesTheToolUnderItsName() {
        for (String reply : List.of("네", "네!", "넵", "예", "응", "맞아요", "네 맞아요", "맞습니다", "소라요", "Sora", "sora 맞아요", "ㅇㅇ")) {
            var analysis = LlamaInterviewClient.confirmationReply(new Answer(2L, AI_TOOL, reply, SORA_QUESTION));
            assertTrue(analysis.isPresent(), reply);
            assertEquals(new InterviewFieldState(AI_TOOL, COMPLETED, "Sora"), analysis.get().state(), reply);
            assertEquals("Sora", analysis.get().evidenceQuote(), reply);
            assertTrue(analysis.get().confirmedName(), reply);
        }
    }

    @Test void noWithAnotherToolTakesThatTool() {
        var midjourney = LlamaInterviewClient.confirmationReply(new Answer(2L, AI_TOOL, "아니요, 미드저니요", SORA_QUESTION)).orElseThrow();
        assertEquals(new InterviewFieldState(AI_TOOL, COMPLETED, "미드저니"), midjourney.state());
        assertFalse(midjourney.confirmedName());

        var custom = LlamaInterviewClient.confirmationReply(new Answer(2L, AI_TOOL, "아뇨 우리팀툴", SORA_QUESTION)).orElseThrow();
        assertEquals("우리팀툴", custom.evidenceQuote());
        assertFalse(custom.confirmedName());
    }

    @Test void aBareNoAsksForTheToolName() {
        for (String reply : List.of("아니요", "아뇨.", "ㄴㄴ", "아니에요")) {
            var analysis = LlamaInterviewClient.confirmationReply(new Answer(2L, AI_TOOL, reply, SORA_QUESTION)).orElseThrow();
            assertEquals(NEEDS_REVIEW, analysis.state().status(), reply);
            assertEquals("", analysis.evidenceQuote(), reply);
            assertEquals("어떤 AI 도구를 사용했나요?", analysis.clarification(), reply);
        }
    }

    @Test void otherRepliesGoToTheModel() {
        for (String reply : List.of("예전에 쓰던 그림 앱이에요", "아니요, AI는 안 썼어요", "소라라는 그림 앱이요", "잘 모르겠어요")) {
            assertTrue(LlamaInterviewClient.confirmationReply(new Answer(2L, AI_TOOL, reply, SORA_QUESTION)).isEmpty(), reply);
        }
        assertTrue(LlamaInterviewClient.confirmationReply(new Answer(2L, AI_TOOL, "네", TOOL_QUESTION)).isEmpty(),
                "only a confirmation question is answered by \"네\"");
    }

    @Test void theReplyToAConfirmationIsNotConfirmedAgain() {
        var analysis = LlamaInterviewClient.acceptBareToolName(new Answer(2L, AI_TOOL, "소라라는 그림 앱이요", SORA_QUESTION),
                completed("소라"));

        assertEquals(COMPLETED, analysis.state().status());
    }
}
