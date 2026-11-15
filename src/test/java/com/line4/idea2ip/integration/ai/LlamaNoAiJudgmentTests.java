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
                JsonMapper.builder().build(), new MockAiInterviewClient());
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

    @Test void aClearNoAiReadingSkipsTheConfirmation() throws IOException {
        var client = clientReplying(completion("{\"intent\":\"NO_AI_USE\"}", "stop"));
        for (String text : List.of("업슴", "없슴", "ㄴㄴ")) {
            var state = client.analyzeAnswer(context(answer(text), List.of()), answer(text));
            assertEquals(NOT_APPLICABLE, state.status(), text);
            assertEquals(InterviewQuestions.NO_AI_JUDGED, state.summary(), text);
        }
        // The model sees the question with the raw answer.
        assertEquals(3, requests.size());
        assertTrue(requests.getFirst().contains("업슴"));
        assertTrue(requests.getFirst().contains("어떤 AI 도구를 사용했나요"));
    }

    @Test void anUnresolvedOrBrokenReplyKeepsTheConfirmation() throws IOException {
        for (String body : List.of(completion("{\"intent\":\"UNRESOLVED\"}", "stop"),
                completion("{\"intent\":\"NO_AI_USE\"}", "length"),
                completion("not json", "stop"),
                completion("{\"intent\":\"NO_AI_USE\",\"extra\":1}", "stop"),
                "{}", "not json at all")) {
            var client = clientReplying(body);
            var state = client.analyzeAnswer(context(answer("ㄴㄴ"), List.of()), answer("ㄴㄴ"));
            assertEquals(NEEDS_REVIEW, state.status(), body);
            stop();
        }
    }

    @Test void anUnreachableModelKeepsTheConfirmation() {
        var client = new LlamaInterviewClient("http://127.0.0.1:9/v1", "test-model", JsonMapper.builder().build(),
                new MockAiInterviewClient());
        var state = client.analyzeAnswer(context(answer("업슴"), List.of()), answer("업슴"));
        assertEquals(NEEDS_REVIEW, state.status());
        var afterTypo = context(answer("업슴"), List.of());
        assertEquals(InterviewQuestions.NO_AI_CONFIRMATION, client.generateQuestion(afterTypo, AI_TOOL).content());
    }

    @Test void theModelIsNotAskedWhenTheRulesAlreadyDecide() throws IOException {
        var client = clientReplying(completion("{\"intent\":\"NO_AI_USE\"}", "stop"));
        // A plain "no AI" and a named tool are settled by rule.
        assertEquals(NOT_APPLICABLE, client.analyzeAnswer(context(answer("AI 안 씀"), List.of()), answer("AI 안 씀")).status());
        assertEquals(COMPLETED, client.analyzeAnswer(context(answer("미드저니"), List.of()), answer("미드저니")).status());
        // A reply to the confirmation question is read as yes or no.
        var reply = new Answer(2L, AI_TOOL, "아니요", InterviewQuestions.NO_AI_CONFIRMATION);
        assertEquals(NEEDS_REVIEW, client.analyzeAnswer(context(reply, List.of()), reply).status());
        assertTrue(requests.isEmpty());
    }

    @Test void registeredAiMaterialsLeaveTheContradictionToTheUser() throws IOException {
        var client = clientReplying(completion("{\"intent\":\"NO_AI_USE\"}", "stop"));
        var registered = List.of(new Evidence("material:1:file:1", MaterialType.AI_ASSISTED, "시안", null, "Midjourney"));
        var state = client.analyzeAnswer(context(answer("업슴"), registered), answer("업슴"));
        assertEquals(NEEDS_REVIEW, state.status());
        assertTrue(requests.isEmpty());
    }

    @Test void oneToolDeniedGoesToTheModelInsteadOfTheRules() throws IOException {
        var client = clientReplying(completion("{\"intent\":\"UNRESOLVED\"}", "stop"));
        var state = client.analyzeAnswer(context(answer("챗GPT는 안 썼어요"), List.of()), answer("챗GPT는 안 썼어요"));
        assertEquals(NEEDS_REVIEW, state.status());
        assertEquals(1, requests.size());
    }
}
