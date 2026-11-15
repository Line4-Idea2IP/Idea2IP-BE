package com.line4.idea2ip.integration.ai;

import com.line4.idea2ip.domain.interview.entity.InterviewField;
import com.line4.idea2ip.integration.ai.AiInterviewClient.Answer;
import com.line4.idea2ip.integration.ai.AiInterviewClient.AskedQuestion;
import com.line4.idea2ip.integration.ai.AiInterviewClient.Context;
import com.line4.idea2ip.integration.ai.AiInterviewClient.PreviousTurn;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.line4.idea2ip.domain.interview.entity.InterviewField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Live question generation against a stub llama.cpp server. Generation and review calls are told apart by the
 * response schema name, and each gets its own scripted replies, so the retry sequence seen in deployment
 * (QUOTE, then repeated SEMANTIC_DUPLICATE) can be replayed.
 */
class LlamaQuestionGenerationTests {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String MIXED_QUESTION = "이 작품을 만드는 데 사용자가 직접 창작하거나 수정한 부분은 무엇이었나요?";
    private static final String CREATED_ANSWER = "사과 단면과 사과 뒤의 녹색 곡선을 직접 그렸습니다.";

    private final Deque<String> generations = new ArrayDeque<>();
    private final Deque<String> reviews = new ArrayDeque<>();
    private final List<JsonNode> generationInputs = new CopyOnWriteArrayList<>();
    private final List<JsonNode> reviewInputs = new CopyOnWriteArrayList<>();
    private String lastGeneration;
    private String lastReview;
    private HttpServer server;

    @AfterEach void stop() {
        if (server != null) server.stop(0);
    }

    private LlamaInterviewClient client() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            JsonNode request = JSON.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String schema = request.path("response_format").path("json_schema").path("name").asString();
            JsonNode input = JSON.readTree(request.path("messages").path(1).path("content").asString());
            String content;
            synchronized (this) {
                if (schema.equals("InterviewQuestion")) {
                    generationInputs.add(input);
                    if (!generations.isEmpty()) lastGeneration = generations.poll();
                    content = lastGeneration;
                } else {
                    reviewInputs.add(input);
                    if (!reviews.isEmpty()) lastReview = reviews.poll();
                    content = lastReview;
                }
            }
            byte[] bytes = ("{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":"
                    + JSON.writeValueAsString(content) + "}}]}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return new LlamaInterviewClient("http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "test-model", JSON);
    }

    private static String question(InterviewField field, String text, List<String> quotes) {
        return JSON.writeValueAsString(Map.of("target_field", field.name(), "question", text,
                "source_ids", List.of(), "quotes", quotes));
    }

    private static String humanReview(String actual, boolean duplicate) {
        return JSON.writeValueAsString(Map.of("actual_field", actual, "unsupported_assumption", false,
                "duplicate", duplicate, "unused_source", false, "misses_clarification", false));
    }

    /** Project 25 in deployment: no AI, then one creation question that also asked about edits. */
    private static Context afterMixedCreationQuestion() {
        var noAi = new Answer(1L, AI_TOOL, "업슴", "AI 도구를 사용했나요?", "");
        var created = new Answer(2L, HUMAN_CREATED_ELEMENT, CREATED_ANSWER, MIXED_QUESTION, CREATED_ANSWER);
        return new Context(1L, "사과와 캔", "s", List.of(noAi, created), List.of(),
                List.of(new AskedQuestion(AI_TOOL, "AI 도구를 사용했나요?"),
                        new AskedQuestion(HUMAN_CREATED_ELEMENT, MIXED_QUESTION)),
                List.of(new PreviousTurn(AI_TOOL, "AI 도구를 사용했나요?", "업슴"),
                        new PreviousTurn(HUMAN_CREATED_ELEMENT, MIXED_QUESTION, CREATED_ANSWER)));
    }

    private static boolean shows(JsonNode input, String text) {
        return input.toString().contains(text);
    }

    @Test void aQuoteMistakeOnlyDropsQuotesAndKeepsTheInterviewContext() throws IOException {
        var client = client();
        generations.add(question(HUMAN_EDITED_EXPRESSION, "‘녹색 곡선을 고쳤다’고 하셨는데 어떻게 바꾸셨나요?",
                List.of("녹색 곡선을 고쳤다")));
        generations.add(question(HUMAN_EDITED_EXPRESSION, "그린 뒤에 직접 바꾼 부분이 있다면 어떻게 바꾸셨나요?", List.of()));
        reviews.add(humanReview("HUMAN_EDITED_EXPRESSION", false));

        var draft = client.generateQuestion(afterMixedCreationQuestion(), HUMAN_EDITED_EXPRESSION);

        assertEquals("그린 뒤에 직접 바꾼 부분이 있다면 어떻게 바꾸셨나요?", draft.content());
        var retry = generationInputs.get(1);
        assertFalse(retry.path("allow_answer_quotes").asBoolean());
        assertTrue(shows(retry.path("history"), CREATED_ANSWER), "the retry still sees what was answered");
        assertTrue(shows(retry.path("questions_asked"), MIXED_QUESTION), "the retry still sees what was asked");
    }

    @Test void everyRetryShowsTheGeneratorWhatTheReviewerJudgesDuplicatesAgainst() throws IOException {
        var client = client();
        generations.add(question(HUMAN_EDITED_EXPRESSION, "직접 수정한 부분이 있다면 어떻게 작업했나요?", List.of()));
        reviews.add(humanReview("OTHER", false));
        reviews.add(humanReview("HUMAN_EDITED_EXPRESSION", true));

        client.generateQuestion(afterMixedCreationQuestion(), HUMAN_EDITED_EXPRESSION);

        assertEquals(generationInputs.size(), reviewInputs.size());
        for (int i = 0; i < generationInputs.size(); i++) {
            var generation = generationInputs.get(i);
            var review = reviewInputs.get(i);
            for (String key : List.of("questions_asked", "history")) {
                assertEquals(review.path(key), generation.path(key), "attempt " + (i + 1) + " " + key);
            }
            assertTrue(shows(generation, MIXED_QUESTION), "attempt " + (i + 1));
            assertTrue(shows(generation, CREATED_ANSWER), "attempt " + (i + 1));
        }
    }

    @Test void aCreationQuestionThatAlsoAsksAboutEditsIsRejectedBeforeReview() throws IOException {
        var client = client();
        var noAi = new Answer(1L, AI_TOOL, "업슴", "AI 도구를 사용했나요?", "");
        var context = new Context(1L, "사과와 캔", "s", List.of(noAi), List.of(),
                List.of(new AskedQuestion(AI_TOOL, "AI 도구를 사용했나요?")),
                List.of(new PreviousTurn(AI_TOOL, "AI 도구를 사용했나요?", "업슴")));
        generations.add(question(HUMAN_CREATED_ELEMENT, MIXED_QUESTION, List.of()));
        generations.add(question(HUMAN_CREATED_ELEMENT, "직접 새로 그린 요소가 있다면 무엇이고 어떻게 그렸나요?", List.of()));
        reviews.add(humanReview("HUMAN_CREATED_ELEMENT", false));

        var draft = client.generateQuestion(context, HUMAN_CREATED_ELEMENT);

        assertEquals("직접 새로 그린 요소가 있다면 무엇이고 어떻게 그렸나요?", draft.content());
        assertEquals(1, reviewInputs.size(), "the mixed question never reached the reviewer");
        assertTrue(shows(generationInputs.get(1).path("validation_feedback"), "수정"));
    }

    @Test void exhaustedAttemptsAskTheFieldsFixedQuestionInsteadOfFailing() throws IOException {
        var client = client();
        generations.add(question(HUMAN_EDITED_EXPRESSION, "직접 수정한 부분이 있다면 어떻게 작업했나요?", List.of()));
        reviews.add(humanReview("HUMAN_EDITED_EXPRESSION", true));
        var context = afterMixedCreationQuestion();

        var draft = client.generateQuestion(context, HUMAN_EDITED_EXPRESSION);

        assertEquals(4, generationInputs.size());
        assertEquals(MockAiInterviewClient.baseQuestion(HUMAN_EDITED_EXPRESSION), draft.content());
        assertEquals(List.of(), draft.sourceIds());
        assertFalse(InterviewQuestions.alreadyAsked(draft.content(),
                context.questions().stream().map(AskedQuestion::content).toList()));
    }

    @Test void anUnreachableModelStillFailsQuestionGeneration() {
        var client = new LlamaInterviewClient("http://127.0.0.1:9/v1", "test-model", JSON);
        assertThrows(org.springframework.web.client.RestClientException.class,
                () -> client.generateQuestion(afterMixedCreationQuestion(), HUMAN_EDITED_EXPRESSION));
    }
}
