package com.line4.idea2ip.integration.ai;

import com.line4.idea2ip.domain.interview.entity.InterviewField;
import com.line4.idea2ip.integration.ai.AiInterviewClient.Answer;
import com.line4.idea2ip.integration.ai.AiInterviewClient.AskedQuestion;
import com.line4.idea2ip.integration.ai.AiInterviewClient.Context;
import com.line4.idea2ip.integration.ai.AiInterviewClient.PreviousTurn;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static com.line4.idea2ip.domain.interview.entity.InterviewField.*;
import static org.junit.jupiter.api.Assertions.*;

class InterviewQuestionsTests {
    private static final String OUTPUT_ANSWER = "캐릭터의 기본 구도를 참고하고 색상과 표정을 직접 수정했어요.";

    /** The E2E/Figma order: AI output use mentions human edits, then human creation runs before human edits. */
    private static Context interviewUpToHumanEdits() {
        var turns = List.of(
                new PreviousTurn(AI_TOOL, "어떤 AI 도구를 사용했나요?", "Stable Diffusion"),
                new PreviousTurn(AI_PURPOSE, "어떤 목적으로 사용했나요?", "구도 시안 비교"),
                new PreviousTurn(AI_OUTPUT_USAGE, "AI 결과를 어떻게 활용했나요?", OUTPUT_ANSWER),
                new PreviousTurn(HUMAN_CREATED_ELEMENT, "직접 새로 만든 요소가 있나요?", "없습니다"),
                new PreviousTurn(HUMAN_CREATED_ELEMENT, "한 가지만 짚어 주세요.", "사슬의 흐름을 직접 구성했습니다."));
        var questions = new ArrayList<AskedQuestion>();
        var answers = new ArrayList<Answer>();
        long id = 1;
        for (var turn : turns) {
            questions.add(new AskedQuestion(turn.field(), turn.question()));
            answers.add(new Answer(id++, turn.field(), turn.answer()));
        }
        return new Context(1L, "가상 작품", "snapshot", answers, List.of(), questions, turns);
    }

    @Test void humanEditsStillSeeTheEditMentionedDuringAiOutputUse() {
        var earlier = InterviewQuestions.earlierTurns(interviewUpToHumanEdits(), HUMAN_EDITED_EXPRESSION);
        assertTrue(earlier.stream().anyMatch(turn -> turn.answer().equals(OUTPUT_ANSWER)));
        assertTrue(earlier.stream().noneMatch(turn -> turn.field() == AI_TOOL));      // unrelated field
        assertTrue(earlier.stream().noneMatch(turn -> turn.answer().equals("없습니다")));  // nothing to build on
        assertTrue(earlier.size() <= InterviewQuestions.EARLIER_TURNS);
        // AI fields only build on AI answers.
        assertTrue(InterviewQuestions.earlierTurns(interviewUpToHumanEdits(), AI_OUTPUT_USAGE).stream()
                .allMatch(turn -> turn.field() == AI_TOOL || turn.field() == AI_PURPOSE));
    }

    @Test void followUpsInOneFieldCannotPushOutTheFirstEditStatement() {
        // AI output use twice (the edit is in the first answer), then human creation three times, then human edits.
        var turns = List.of(
                new PreviousTurn(AI_OUTPUT_USAGE, "AI 결과를 어떻게 활용했나요?", OUTPUT_ANSWER),
                new PreviousTurn(AI_OUTPUT_USAGE, "어느 단계에서 사용했나요?", "구도를 정할 때 참고했어요."),
                new PreviousTurn(HUMAN_CREATED_ELEMENT, "직접 만든 요소가 있나요?", "사슬을 그렸어요."),
                new PreviousTurn(HUMAN_CREATED_ELEMENT, "한 가지만 짚어 주세요.", "사슬의 흐름을 구성했어요."),
                new PreviousTurn(HUMAN_CREATED_ELEMENT, "어디에 배치했나요?", "손과 시계 사이에 두었어요."));
        var context = new Context(1L, "가상 작품", "snapshot", List.of(), List.of(), List.of(), turns);
        var earlier = InterviewQuestions.earlierTurns(context, HUMAN_EDITED_EXPRESSION);
        assertEquals(OUTPUT_ANSWER, earlier.getFirst().answer());
        assertTrue(earlier.size() <= InterviewQuestions.EARLIER_TURNS);
        assertEquals("손과 시계 사이에 두었어요.", earlier.getLast().answer());  // still oldest first, newest kept
    }

    @Test void overTheLimitEachFieldKeepsItsFirstAndLatestAnswer() {
        // Nine related turns (every field asked three times); only six fit.
        var turns = List.of(
                new PreviousTurn(AI_PURPOSE, "목적은요?", "구도 시안 비교"),
                new PreviousTurn(AI_PURPOSE, "한 가지만요?", "손 배치 비교"),
                new PreviousTurn(AI_PURPOSE, "해결하려던 문제는요?", "포즈가 어색했어요"),
                new PreviousTurn(AI_OUTPUT_USAGE, "결과를 어떻게 썼나요?", OUTPUT_ANSWER),
                new PreviousTurn(AI_OUTPUT_USAGE, "어느 단계에서요?", "구도를 정할 때요"),
                new PreviousTurn(AI_OUTPUT_USAGE, "그 외에 더 있나요?", "없습니다"),
                new PreviousTurn(HUMAN_CREATED_ELEMENT, "직접 만든 요소는요?", "사슬을 그렸어요"),
                new PreviousTurn(HUMAN_CREATED_ELEMENT, "한 가지만요?", "사슬의 흐름이요"),
                new PreviousTurn(HUMAN_CREATED_ELEMENT, "어디에 두었나요?", "손과 시계 사이요"));
        var context = new Context(1L, "가상 작품", "snapshot", List.of(), List.of(), List.of(), turns);
        var answers = InterviewQuestions.earlierTurns(context, HUMAN_EDITED_EXPRESSION).stream()
                .map(PreviousTurn::answer).toList();
        assertEquals(InterviewQuestions.EARLIER_TURNS, answers.size());
        // A "most recent six" selection would drop the AI purpose field entirely.
        assertEquals(List.of("구도 시안 비교", "포즈가 어색했어요", OUTPUT_ANSWER, "없습니다", "사슬을 그렸어요", "손과 시계 사이요"),
                answers);
    }

    @Test void aLaterNoneIsKeptBesideTheStatementItQualifies() {
        var turns = List.of(
                new PreviousTurn(AI_OUTPUT_USAGE, "결과를 어떻게 썼나요?", OUTPUT_ANSWER),
                new PreviousTurn(AI_OUTPUT_USAGE, "직접 수정한 게 맞나요?", "없습니다"),
                new PreviousTurn(HUMAN_CREATED_ELEMENT, "직접 만든 요소는요?", "없습니다"));
        var context = new Context(1L, "가상 작품", "snapshot", List.of(), List.of(), List.of(), turns);
        var earlier = InterviewQuestions.earlierTurns(context, HUMAN_EDITED_EXPRESSION);
        // The correction travels with its question; an opening "없음" of a field is still left out.
        assertEquals(2, earlier.size());
        assertEquals("직접 수정한 게 맞나요?", earlier.get(1).question());
        assertEquals("없습니다", earlier.get(1).answer());
    }

    @Test void exhaustedCandidatesNeverRepeatAQuestion() {
        String base = MockAiInterviewClient.baseQuestion(AI_PURPOSE);
        var asked = new ArrayList<String>(List.of(base));
        asked.add(InterviewQuestions.unused(AI_PURPOSE, base, asked));
        asked.add(InterviewQuestions.unused(AI_PURPOSE, base, asked));  // base and both alternatives used
        // Three closing prompts, then numbered ones: never an exception, never a repeat.
        for (int i = 0; i < 5; i++) {
            String next = InterviewQuestions.unused(AI_PURPOSE, base, asked);
            assertFalse(InterviewQuestions.alreadyAsked(next, asked), next);
            assertTrue(next.contains("AI 활용 목적"));
            assertTrue(InterviewQuestions.isClosingQuestion(AI_PURPOSE, next), next);
            asked.add(next);
        }
        assertTrue(asked.getLast().startsWith("(3번째 확인)"), asked.getLast());
    }

    @Test void noneToAClosingQuestionLeavesTheFieldForReview() {
        var client = new MockAiInterviewClient();
        String closing = InterviewQuestions.unused(HUMAN_EDITED_EXPRESSION, "이미 물은 질문",
                List.of("이미 물은 질문", MockAiInterviewClient.baseQuestion(HUMAN_EDITED_EXPRESSION),
                        InterviewQuestions.followUp(HUMAN_EDITED_EXPRESSION, List.of(), java.util.Optional.empty()),
                        "직접 바꾼 부분의 수정 전과 후가 어떻게 달랐나요? 바꾼 부분이 없다면 ‘없음’이라고 알려주세요."));
        assertTrue(InterviewQuestions.isClosingQuestion(HUMAN_EDITED_EXPRESSION, closing));

        var earlier = new Answer(1L, HUMAN_EDITED_EXPRESSION, "조금", "직접 수정한 부분이 있나요?");
        var reply = new Answer(2L, HUMAN_EDITED_EXPRESSION, "없음", closing);
        var context = new Context(1L, "가상 작품", "snapshot", List.of(earlier, reply), List.of());
        var state = client.analyzeAnswer(context, reply);
        assertEquals(com.line4.idea2ip.domain.interview.entity.InterviewFieldState.Status.NEEDS_REVIEW, state.status());
        assertTrue(state.closed());
        assertEquals("조금 / 없음", state.summary());

        // The same "없음" to an ordinary question still means the work does not exist.
        var ordinary = new Answer(3L, HUMAN_EDITED_EXPRESSION, "없음", "직접 수정한 부분이 있나요?");
        assertEquals(com.line4.idea2ip.domain.interview.entity.InterviewFieldState.Status.NOT_APPLICABLE,
                client.analyzeAnswer(new Context(1L, "가상 작품", "snapshot", List.of(ordinary), List.of()), ordinary).status());
    }

    @Test void closingPromptsCarryTheClosingReason() {
        var questions = new ArrayList<AskedQuestion>();
        questions.add(new AskedQuestion(AI_TOOL, MockAiInterviewClient.baseQuestion(AI_TOOL)));
        questions.add(new AskedQuestion(AI_TOOL, InterviewQuestions.followUp(AI_TOOL, List.of(), java.util.Optional.empty())));
        questions.add(new AskedQuestion(AI_TOOL, "작업할 때 사용한 AI 앱이나 서비스의 이름을 알려주실 수 있나요?"));
        var answers = List.of(new Answer(1L, AI_TOOL, "음"), new Answer(2L, AI_TOOL, "글쎄"));
        var context = new Context(1L, "가상 작품", "snapshot", answers, List.of(), questions, List.of());
        var draft = new MockAiInterviewClient().generateQuestion(context, AI_TOOL);
        assertTrue(InterviewQuestions.isClosingQuestion(AI_TOOL, draft.content()), draft.content());
        assertEquals(InterviewQuestions.CLOSING_REASON, draft.reason());
    }

    @Test void replacementIsCheckedAgainstRecentQuestionsToo() {
        String base = MockAiInterviewClient.baseQuestion(HUMAN_EDITED_EXPRESSION);
        String replacement = InterviewQuestions.unused(HUMAN_EDITED_EXPRESSION, base, List.of(base));
        assertNotEquals(base, replacement);
        assertFalse(InterviewQuestions.alreadyAsked(replacement, List.of(base)));
    }

    @Test void followUpsNeverRepeatAQuestionOfTheField() {
        var asked = new ArrayList<String>();
        asked.add(MockAiInterviewClient.baseQuestion(AI_PURPOSE));
        for (int i = 0; i < 2; i++) {
            String next = InterviewQuestions.followUp(AI_PURPOSE, asked, Optional.empty());
            assertFalse(InterviewQuestions.alreadyAsked(next, asked), next);
            asked.add(next);
        }
        assertTrue(InterviewQuestions.alreadyAsked(" 그 AI 도구를, 어떤 작업에 어떤 목적으로 사용했나요?! ",
                List.of(MockAiInterviewClient.baseQuestion(AI_PURPOSE))));
    }

    @Test void liveReplacementSkipsABaseQuestionThatWasAlreadyAsked() throws Exception {
        var mapper = JsonMapper.builder().build();
        String base = MockAiInterviewClient.baseQuestion(HUMAN_EDITED_EXPRESSION);
        // The base question already appeared, and the model echoes it.
        var context = new Context(1L, "가상 작품", "snapshot", List.of(), List.of(),
                List.of(new AskedQuestion(HUMAN_CREATED_ELEMENT, base)), List.of());
        var server = echoServer(mapper, base);
        try {
            var client = new LlamaInterviewClient("http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                    "test-model", mapper, new MockAiInterviewClient());
            String question = client.generateQuestion(context, HUMAN_EDITED_EXPRESSION).content();
            assertNotEquals(base, question);
            assertFalse(InterviewQuestions.alreadyAsked(question, List.of(base)));
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer echoServer(JsonMapper mapper, String question) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            String draft = mapper.writeValueAsString(Map.of("target_field", "HUMAN_EDITED_EXPRESSION",
                    "question", question, "source_ids", List.of()));
            byte[] body = mapper.writeValueAsBytes(Map.of("choices", List.of(Map.of("finish_reason", "stop",
                    "message", Map.of("content", draft)))));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        return server;
    }

    @Test void liveClientSendsEarlierTurnsAndReplacesARepeatedQuestion() throws Exception {
        // A new AI field with no deterministic opening: the model writes it from the earlier AI answers.
        var mapper = JsonMapper.builder().build();
        var turns = List.of(new PreviousTurn(AI_TOOL, "어떤 AI 도구를 사용했나요?", "Stable Diffusion"),
                new PreviousTurn(AI_PURPOSE, "어떤 목적으로 사용했나요?", "손 위치가 다른 시안을 비교했어요."));
        var context = new Context(1L, "가상 작품", "snapshot",
                List.of(new Answer(1L, AI_TOOL, "Stable Diffusion"), new Answer(2L, AI_PURPOSE, "손 위치가 다른 시안을 비교했어요.")),
                List.of(), turns.stream().map(turn -> new AskedQuestion(turn.field(), turn.question())).toList(), turns);
        String repeated = context.questions().getLast().content();  // the model echoes the previous question
        var request = new AtomicReference<JsonNode>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            request.set(mapper.readTree(exchange.getRequestBody().readAllBytes()));
            String draft = mapper.writeValueAsString(Map.of("target_field", "AI_OUTPUT_USAGE",
                    "question", repeated, "source_ids", List.of()));
            byte[] body = mapper.writeValueAsBytes(Map.of("choices", List.of(Map.of("finish_reason", "stop",
                    "message", Map.of("content", draft)))));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        try {
            var client = new LlamaInterviewClient("http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                    "test-model", mapper, new MockAiInterviewClient());
            var draft = client.generateQuestion(context, AI_OUTPUT_USAGE);

            JsonNode input = mapper.readTree(request.get().path("messages").path(1).path("content").asString());
            var earlier = new ArrayList<String>();
            input.path("earlierTurns").forEach(turn -> earlier.add(turn.path("answer").asString()));
            assertTrue(earlier.contains("손 위치가 다른 시안을 비교했어요."), earlier.toString());
            assertTrue(input.path("recentQuestions").toString().contains(repeated));

            assertEquals(MockAiInterviewClient.baseQuestion(AI_OUTPUT_USAGE), draft.content());
        } finally {
            server.stop(0);
        }
    }

}
