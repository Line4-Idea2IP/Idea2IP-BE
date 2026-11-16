package com.line4.idea2ip.integration.ai;

import com.line4.idea2ip.domain.interview.entity.InterviewFieldState;
import com.line4.idea2ip.integration.ai.AiInterviewClient.Answer;
import com.line4.idea2ip.integration.ai.AiInterviewClient.AnswerAnalysis;
import com.line4.idea2ip.integration.ai.AiInterviewClient.AskedQuestion;
import com.line4.idea2ip.integration.ai.AiInterviewClient.Context;
import com.line4.idea2ip.integration.ai.AiInterviewClient.PreviousTurn;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.line4.idea2ip.domain.interview.entity.InterviewField.AI_PURPOSE;
import static com.line4.idea2ip.domain.interview.entity.InterviewField.AI_TOOL;
import static com.line4.idea2ip.domain.interview.entity.InterviewFieldState.Status.COMPLETED;
import static com.line4.idea2ip.domain.interview.entity.InterviewFieldState.Status.NEEDS_REVIEW;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Deployed interview: the user answered "gpt" and was asked for a tool name again. The real model, with no tool
 * registered on the materials, judged "gpt" unresolved and returned "gpt는 어떤 AI 도구인가요?".
 */
class BareToolNameTests {
    private static final String TOOL_QUESTION = MockAiInterviewClient.baseQuestion(AI_TOOL);

    private static AnswerAnalysis unresolved(String clarification) {
        return new AnswerAnalysis(new InterviewFieldState(AI_TOOL, NEEDS_REVIEW, "답변의 의미를 추가로 확인해야 합니다."), "", clarification);
    }

    @Test void aBareToolNameSettlesTheToolQuestion() {
        var analysis = LlamaInterviewClient.acceptBareToolName(new Answer(1L, AI_TOOL, "gpt", TOOL_QUESTION),
                unresolved("gpt는 어떤 AI 도구인가요?"));

        assertEquals(new InterviewFieldState(AI_TOOL, COMPLETED, "gpt"), analysis.state());
        assertEquals("gpt", analysis.evidenceQuote());
        assertNull(analysis.clarification());
    }

    @Test void trailingPunctuationIsNotPartOfTheName() {
        var analysis = LlamaInterviewClient.acceptBareToolName(new Answer(1L, AI_TOOL, "GPT.", TOOL_QUESTION),
                unresolved("어떤 도구인가요?"));

        assertEquals("GPT", analysis.evidenceQuote());
    }

    @Test void aSentenceNamingAKnownToolSettlesItWithTheNamesOnly() {
        var one = LlamaInterviewClient.acceptBareToolName(new Answer(1L, AI_TOOL, "제미나이로 시안을 뽑았어요", TOOL_QUESTION),
                unresolved("어떤 도구인가요?"));
        var two = LlamaInterviewClient.acceptBareToolName(new Answer(1L, AI_TOOL, "gpt랑 클로드 둘 다 써봤어요", TOOL_QUESTION),
                unresolved("어떤 도구인가요?"));

        assertEquals("제미나이", one.evidenceQuote());
        assertEquals("gpt랑 클로드", two.evidenceQuote());
        assertEquals(COMPLETED, two.state().status());
    }

    // Real model: "클로드랑 미드저니 썼습니다" was quoted as "클로드" only.
    @Test void everyKnownToolTheAnswerNamesStaysInTheQuote() {
        var settled = new AnswerAnalysis(new InterviewFieldState(AI_TOOL, COMPLETED, "클로드"), "클로드", null);

        var analysis = LlamaInterviewClient.acceptBareToolName(new Answer(1L, AI_TOOL, "클로드랑 미드저니 썼습니다", TOOL_QUESTION), settled);

        assertEquals(new InterviewFieldState(AI_TOOL, COMPLETED, "클로드랑 미드저니"), analysis.state());
        assertEquals("클로드랑 미드저니", analysis.evidenceQuote());
        var single = new AnswerAnalysis(new InterviewFieldState(AI_TOOL, COMPLETED, "gpt"), "gpt", null);
        assertSame(single, LlamaInterviewClient.acceptBareToolName(new Answer(1L, AI_TOOL, "gpt 썼어요", TOOL_QUESTION), single));
    }

    @Test void aSentenceDenyingUseStaysWithTheModel() {
        for (String reply : List.of("GPT는 안 썼어요", "제미나이 말고 다른 거요", "클로드는 사용하지 않았습니다", "미드저니는 없어요")) {
            var analysis = LlamaInterviewClient.acceptBareToolName(new Answer(1L, AI_TOOL, reply, TOOL_QUESTION),
                    unresolved("다른 AI 도구를 사용했는지"));
            assertEquals(NEEDS_REVIEW, analysis.state().status(), reply);
        }
    }

    @Test void answersThatAreNotToolNamesStayWithTheModel() {
        for (String reply : List.of("네", "없슴", "ㄴㄴ", "아니요", "GPT는 안 썼어요", "그냥 여러 개 써봤어요")) {
            var analysis = LlamaInterviewClient.acceptBareToolName(new Answer(1L, AI_TOOL, reply, TOOL_QUESTION),
                    unresolved("어떤 도구인가요?"));
            assertEquals(NEEDS_REVIEW, analysis.state().status(), reply);
        }
        var purpose = LlamaInterviewClient.acceptBareToolName(new Answer(1L, AI_PURPOSE, "gpt", "목적은?"),
                new AnswerAnalysis(new InterviewFieldState(AI_PURPOSE, NEEDS_REVIEW, "확인"), "", "목적은?"));
        assertEquals(NEEDS_REVIEW, purpose.state().status(), "only the tool question takes a bare name");
    }

    @Test void aQuestionTheCheckWroteFollowsOnFromTheUsersWords() {
        var context = new Context(1L, "작품", "s", List.of(new Answer(1L, AI_TOOL, "그림 프로그램", TOOL_QUESTION)), List.of(),
                List.of(new AskedQuestion(AI_TOOL, TOOL_QUESTION)), List.of(new PreviousTurn(AI_TOOL, TOOL_QUESTION, "그림 프로그램")));

        var draft = InterviewQuestionTemplates.draft(context, AI_TOOL, "어떤 AI 기능을 사용했나요?");

        assertEquals("‘그림 프로그램’라고 하셨는데, 어떤 AI 기능을 사용했나요?", draft.content());
        assertEquals(InterviewQuestionTemplates.CLARIFY_REASON, draft.reason());
    }

    @Test void anInstructionWrittenAsAQuestionIsNotShown() {
        var context = new Context(1L, "작품", "s", List.of(new Answer(1L, AI_TOOL, "그림 프로그램", TOOL_QUESTION)), List.of(),
                List.of(new AskedQuestion(AI_TOOL, TOOL_QUESTION)), List.of());

        var draft = InterviewQuestionTemplates.draft(context, AI_TOOL, "사용자에게 도구 이름을 확인하세요?");

        assertFalse(draft.content().contains("사용자"));
    }
}
