package com.line4.idea2ip.integration.ai;

import com.line4.idea2ip.domain.material.entity.MaterialType;
import com.line4.idea2ip.integration.ai.AiInterviewClient.Answer;
import com.line4.idea2ip.integration.ai.AiInterviewClient.AskedQuestion;
import com.line4.idea2ip.integration.ai.AiInterviewClient.Context;
import com.line4.idea2ip.integration.ai.AiInterviewClient.Evidence;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.line4.idea2ip.domain.interview.entity.InterviewField.AI_TOOL;
import static com.line4.idea2ip.domain.interview.entity.InterviewFieldState.Status.*;
import static org.junit.jupiter.api.Assertions.*;

/** The live no-AI judgment against a stub llama.cpp server that returns a fixed chat completion. */
class LlamaNoAiJudgmentTests {
    private static final String BASE = MockAiInterviewClient.baseQuestion(AI_TOOL);
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private HttpServer server;

    @AfterEach void stop() {
        if (server != null) server.stop(0);
        server = null;
    }

    private LlamaInterviewClient clientReplying(String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return new LlamaInterviewClient("http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "test-model",
                JsonMapper.builder().build());
    }

    private static String completion(String content, String finishReason) {
        return "{\"choices\":[{\"finish_reason\":\"" + finishReason + "\",\"message\":{\"content\":"
                + JsonMapper.builder().build().writeValueAsString(content) + "}}]}";
    }

    private static Context context(Answer answer, List<Evidence> evidence) {
        return new Context(1L, "작품", "s", List.of(answer), evidence,
                List.of(new AskedQuestion(AI_TOOL, answer.question())), List.of());
    }

    private static Answer answer(String text) {
        return new Answer(1L, AI_TOOL, text, BASE);
    }

    private static String answerJson(String intent, String quote, String clarification) {
        return JsonMapper.builder().build().writeValueAsString(java.util.Map.of(
                "intent", intent, "quote", quote, "clarification", clarification, "missing_details", false));
    }

    @Test void everyLiveAnswerIsInterpretedWithItsQuestion() throws IOException {
        var client = clientReplying(completion(answerJson("NO_AI_USE", "", ""), "stop"));
        for (String text : List.of("업슴", "없슴", "ㄴㄴ", "없음", "전부 손으로 작업했어요")) {
            var answer = answer(text);
            var result = client.evaluate(context(answer, List.of()), answer);
            assertEquals(NOT_APPLICABLE, result.state().status(), text);
            assertEquals("", result.evidenceQuote());
        }
        assertEquals(5, requests.size());
        assertTrue(requests.getLast().contains("전부 손으로 작업했어요"));
        assertTrue(requests.getLast().contains("어떤 AI 도구를 사용했나요"));
    }

    @Test void aNamedToolUsesOnlyTheOriginalEvidenceQuote() throws IOException {
        var client = clientReplying(completion(answerJson("ANSWERED", "스테이블 디퓨젼", ""), "stop"));
        var reply = new Answer(2L, AI_TOOL, "스테이블 디퓨젼 썼어요", InterviewQuestions.NO_AI_CONFIRMATION);
        var result = client.evaluate(context(reply, List.of()), reply);
        assertEquals(COMPLETED, result.state().status());
        assertEquals("스테이블 디퓨젼", result.evidenceQuote());
        assertEquals(1, requests.size());
    }

    @Test void unresolvedMeaningSuppliesAClarificationTopic() throws IOException {
        var client = clientReplying(completion(answerJson("UNRESOLVED", "", "다른 도구를 사용했는지"), "stop"));
        var answer = answer("챗GPT는 안 썼어요");
        var result = client.evaluate(context(answer, List.of()), answer);
        assertEquals(NEEDS_REVIEW, result.state().status());
        assertEquals("다른 도구를 사용했는지", result.clarification());
        assertEquals(1, requests.size());
    }

    @Test void malformedOrUngroundedModelRepliesAreRejected() throws IOException {
        for (String body : List.of(completion(answerJson("NO_AI_USE", "", ""), "length"),
                completion(answerJson("ANSWERED", "Midjourney", ""), "stop"),
                completion("not json", "stop"), "{}")) {
            var client = clientReplying(body);
            var answer = answer("ㄴㄴ");
            assertThrows(IllegalStateException.class, () -> client.evaluate(context(answer, List.of()), answer));
            stop();
        }
    }

    @Test void anUnreachableModelLeavesTheAnswerForRetry() {
        var client = new LlamaInterviewClient("http://127.0.0.1:9/v1", "test-model", JsonMapper.builder().build());
        var answer = answer("업슴");
        assertThrows(org.springframework.web.client.RestClientException.class,
                () -> client.evaluate(context(answer, List.of()), answer));
    }

    @Test void registeredAiMaterialsPreventAnAutomaticNoAiDecision() throws IOException {
        var client = clientReplying(completion(answerJson("NO_AI_USE", "", ""), "stop"));
        var registered = List.of(new Evidence("material:1:file:1", MaterialType.AI_ASSISTED, "시안", null, "Midjourney"));
        var answer = answer("업슴");
        assertEquals(NEEDS_REVIEW, client.evaluate(context(answer, registered), answer).state().status());
        assertEquals(1, requests.size());
    }
}
