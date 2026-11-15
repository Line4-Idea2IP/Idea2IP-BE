package com.line4.idea2ip.integration.ai;

import com.line4.idea2ip.domain.interview.entity.InterviewFieldState;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.line4.idea2ip.domain.interview.entity.InterviewField.*;
import static com.line4.idea2ip.domain.interview.entity.InterviewFieldState.Status.COMPLETED;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Replays the deployed typo interview: the tool answer already said what the AI did and that its images stayed
 * out of the final work, and an edit answer full of typos was clear enough to accept.
 */
class LlamaCoveredFieldTests {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String TOOL_QUESTION = "작품을 만드는 과정에서 AI 도구를 사용했나요? 사용했다면 어떤 도구를 사용했나요?";
    private static final String TOOL_ANSWER = "스테이블 디퓨젼 썻어여. 사과랑 캔 배치 비교용으로만 썻구, 생성 이미지는 최종작품에 안 넣엇어요.";
    private static final String EDIT_ANSWER = "하단 캔뚜껑 테두리를 회색애서 청회색으루 바궛고 흰색을 얇게 덧칠햇어요.";

    private final List<JsonNode> inputs = new CopyOnWriteArrayList<>();
    private final List<String> schemas = new CopyOnWriteArrayList<>();
    private HttpServer server;

    @AfterEach void stop() {
        if (server != null) server.stop(0);
    }

    private LlamaInterviewClient client(String reply) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            JsonNode request = JSON.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            schemas.add(request.path("response_format").path("json_schema").path("name").asString());
            inputs.add(JSON.readTree(request.path("messages").path(1).path("content").asString()));
            byte[] bytes = ("{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":"
                    + JSON.writeValueAsString(reply) + "}}]}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return new LlamaInterviewClient("http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "test-model", JSON);
    }

    private static String coverage(String coverage, String quote) {
        return JSON.writeValueAsString(Map.of("coverage", coverage, "quote", quote));
    }

    private static Context afterToolAnswer() {
        return new Context(1L, "사과와 캔", "s",
                List.of(new Answer(1L, AI_TOOL, TOOL_ANSWER, TOOL_QUESTION, "스테이블 디퓨젼")), List.of(),
                List.of(new AskedQuestion(AI_TOOL, TOOL_QUESTION)),
                List.of(new PreviousTurn(AI_TOOL, TOOL_QUESTION, TOOL_ANSWER)));
    }

    @Test void anOutputUseTheToolAnswerAlreadyStatedIsSettledInTheUsersWords() throws IOException {
        var state = client(coverage("COVERED", "생성 이미지는 최종작품에 안 넣었어요")).settleFromHistory(afterToolAnswer(), AI_OUTPUT_USAGE);

        assertEquals(new InterviewFieldState(AI_OUTPUT_USAGE, COMPLETED, "생성 이미지는 최종작품에 안 넣엇어요"), state);
        assertEquals(List.of("InterviewCoverage"), schemas);
        assertEquals("AI_OUTPUT_USAGE", inputs.getFirst().path("target_field").asString());
        assertEquals(TOOL_ANSWER, inputs.getFirst().path("history").path(0).path("answer").asString());
    }

    @Test void anAllInOneToolAnswerAlsoSettlesTheHumanFieldsWithTheirOwnSentences() throws IOException {
        String allInOne = "Adobe Firefly를 사용했습니다. AI 이미지 자체는 최종 작품에 넣지 않았습니다. "
                + "사과 단면은 연필로 윤곽을 잡고 얇은 붓으로 밝은 과육을 직접 그렸습니다. "
                + "캔 뚜껑 테두리는 회색에서 청회색으로 바꾸고 반사광에는 흰색을 덧칠했습니다.";
        var context = new Context(1L, "사과와 캔", "s",
                List.of(new Answer(1L, AI_TOOL, allInOne, TOOL_QUESTION, "Adobe Firefly")), List.of(),
                List.of(new AskedQuestion(AI_TOOL, TOOL_QUESTION)),
                List.of(new PreviousTurn(AI_TOOL, TOOL_QUESTION, allInOne)));
        String edit = "캔 뚜껑 테두리는 회색에서 청회색으로 바꾸고 반사광에는 흰색을 덧칠했습니다";

        var state = client(coverage("COVERED", edit)).settleFromHistory(context, HUMAN_EDITED_EXPRESSION);

        assertEquals(new InterviewFieldState(HUMAN_EDITED_EXPRESSION, COMPLETED, edit), state);
    }

    @Test void aFieldTheEarlierAnswersDoNotCoverIsStillAsked() throws IOException {
        assertNull(client(coverage("NOT_COVERED", "")).settleFromHistory(afterToolAnswer(), HUMAN_CREATED_ELEMENT));
    }

    @Test void aQuoteMissingFromTheAnswersSettlesNothing() throws IOException {
        assertNull(client(coverage("COVERED", "AI 결과를 배경에 그대로 사용했습니다"))
                .settleFromHistory(afterToolAnswer(), AI_OUTPUT_USAGE));
    }

    @Test void theToolNameThatAnsweredTheToolFieldDoesNotAlsoAnswerThePurpose() throws IOException {
        assertNull(client(coverage("COVERED", "스테이블 디퓨젼")).settleFromHistory(afterToolAnswer(), AI_PURPOSE));
    }

    @Test void noEarlierAnswerMeansNoModelCall() throws IOException {
        var empty = new Context(1L, "사과와 캔", "s", List.of(), List.of(), List.of(), List.of());

        assertNull(client(coverage("COVERED", "x")).settleFromHistory(empty, AI_TOOL));
        assertTrue(schemas.isEmpty());
    }

    @Test void anEditAnswerWithTyposIsAcceptedWithItsOriginalWording() throws IOException {
        String corrected = "하단 캔뚜껑 테두리를 회색에서 청회색으로 바꿨고 흰색을 얇게 덧칠했어요";
        var reply = JSON.writeValueAsString(Map.of("intent", "ANSWERED", "quote", corrected,
                "clarification", "", "missing_details", false));
        var answer = new Answer(9L, HUMAN_EDITED_EXPRESSION, EDIT_ANSWER, "직접 수정하거나 추가한 표현이 있다면 무엇을 어떻게 작업했나요?");
        var context = new Context(1L, "사과와 캔", "s", List.of(answer), List.of(), List.of(), List.of());

        var analysis = client(reply).evaluate(context, answer);

        String original = "하단 캔뚜껑 테두리를 회색애서 청회색으루 바궛고 흰색을 얇게 덧칠햇어요";
        assertEquals(new InterviewFieldState(HUMAN_EDITED_EXPRESSION, COMPLETED, original), analysis.state());
        assertEquals(original, analysis.evidenceQuote());
        assertEquals(1, schemas.size(), "the typo-corrected quote is accepted without a re-check");
    }
}
