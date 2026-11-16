package com.line4.idea2ip.integration.ai;

import com.line4.idea2ip.domain.interview.entity.InterviewField;
import com.line4.idea2ip.integration.ai.AiInterviewClient.Answer;
import com.line4.idea2ip.integration.ai.AiInterviewClient.Context;
import com.line4.idea2ip.integration.ai.AiInterviewClient.Finding;
import com.line4.idea2ip.integration.ai.AiInterviewClient.InputText;
import com.line4.idea2ip.integration.ai.AiInterviewClient.ProjectInputs;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.line4.idea2ip.domain.interview.entity.InterviewField.*;
import static com.line4.idea2ip.domain.interview.entity.InterviewFieldState.Status.COMPLETED;
import static com.line4.idea2ip.domain.interview.entity.InterviewFieldState.Status.NEEDS_REVIEW;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Evidence search against a stub model. The stub's findings replay what the deployed model returned
 * (projects 32 and 33); the client must keep only findings it can verify.
 */
class LlamaFindingTests {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String TOOL_QUESTION = "작품을 만드는 과정에서 AI 도구를 사용했나요? 사용했다면 어떤 도구를 사용했나요?";
    private static final String TYPO_ANSWER = "스테이블 디퓨젼 썻어여. 사과랑 캔 배치 비교용으로만 썻구, 생성 이미지는 최종작품에 안 넣엇어요.";
    private static final EnumSet<InterviewField> AFTER_TOOL =
            EnumSet.of(AI_PURPOSE, AI_OUTPUT_USAGE, HUMAN_CREATED_ELEMENT, HUMAN_EDITED_EXPRESSION);

    private final List<String> schemas = new CopyOnWriteArrayList<>();
    private final List<JsonNode> inputs = new CopyOnWriteArrayList<>();
    private boolean checkAnswer = true;
    private HttpServer server;

    @AfterEach void stop() {
        if (server != null) server.stop(0);
    }

    private LlamaInterviewClient client(String findings) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            JsonNode request = JSON.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String schema = request.path("response_format").path("json_schema").path("name").asString();
            schemas.add(schema);
            inputs.add(JSON.readTree(request.path("messages").path(1).path("content").asString()));
            String content = schema.equals("InterviewFindingCheck")
                    ? JSON.writeValueAsString(Map.of("answers_field", checkAnswer)) : findings;
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

    /** Findings JSON: field → {status, quote, gap, source}; unlisted fields are NONE. */
    private static String findings(String source, Map<InterviewField, String[]> found) {
        var result = new LinkedHashMap<String, Object>();
        for (InterviewField field : InterviewField.values()) {
            String[] value = found.get(field);
            result.put(field.name(), value == null
                    ? Map.of("status", "NONE", "quote", "", "gap", "", "source", "")
                    : Map.of("status", value[0], "quote", value[1], "gap", value.length > 2 ? value[2] : "", "source", source));
        }
        return JSON.writeValueAsString(result);
    }

    private static Answer toolAnswer(String text) {
        return new Answer(7L, AI_TOOL, text, TOOL_QUESTION, "스테이블 디퓨젼");
    }

    private static Context context(Answer answer) {
        return new Context(1L, "사과와 캔", "s", List.of(answer), List.of(), List.of(), List.of());
    }

    @Test void theToolAnswerAlsoSettlesPurposeAndOutputUseInTheUsersOwnWords() throws IOException {
        var answer = toolAnswer(TYPO_ANSWER);
        var reply = findings("answer:7", Map.of(
                AI_PURPOSE, new String[]{"FOUND", "사과랑 캔 배치 비교용으로만 썻구"},
                AI_OUTPUT_USAGE, new String[]{"FOUND", "생성 이미지는 최종작품에 안 넣었어요"}));

        var found = client(reply).findInAnswer(context(answer), answer, AFTER_TOOL, List.of());

        assertEquals(List.of(
                new Finding(AI_PURPOSE, COMPLETED, "사과랑 캔 배치 비교용으로만 썻구", "answer:7", null),
                new Finding(AI_OUTPUT_USAGE, COMPLETED, "생성 이미지는 최종작품에 안 넣엇어요", "answer:7", null)), found);
        assertEquals(List.of("InterviewFindings", "InterviewFindingCheck", "InterviewFindingCheck"), schemas);
        assertEquals("생성 이미지는 최종작품에 안 넣엇어요", inputs.get(2).path("sentence").asString(),
                "the isolated check sees only the sentence");
    }

    // Deployment, project 32: the output-use sentence also "settled" the creation and edit fields.
    @Test void aSentenceSettlesOnlyOneField() throws IOException {
        var answer = toolAnswer(TYPO_ANSWER);
        var reply = findings("answer:7", Map.of(
                AI_OUTPUT_USAGE, new String[]{"FOUND", "생성 이미지는 최종작품에 안 넣엇어요"},
                HUMAN_CREATED_ELEMENT, new String[]{"FOUND", "사과랑 캔 배치 비교용으로만 썻구, 생성 이미지는 최종작품에 안 넣엇어요."},
                HUMAN_EDITED_EXPRESSION, new String[]{"FOUND", "생성 이미지는 최종작품에 안 넣엇어요."}));

        var found = client(reply).findInAnswer(context(answer), answer, AFTER_TOOL, List.of());

        assertEquals(List.of(AI_OUTPUT_USAGE), found.stream().map(Finding::field).toList());
    }

    // Deployment, project 33: the purpose sentence "settled" the output-use field the user never answered.
    @Test void aSentenceAlreadyClaimedByAnotherFieldIsRejected() throws IOException {
        String purposeOnly = "Adobe Firefly를 사용했습니다. 초안 단계에서 사과와 음료 캔의 대각선 구도와 원형 구도를 비교하려고 사용했습니다.";
        var answer = new Answer(7L, AI_TOOL, purposeOnly, TOOL_QUESTION, "Adobe Firefly");
        var reply = findings("answer:7", Map.of(
                AI_PURPOSE, new String[]{"FOUND", purposeOnly},
                AI_OUTPUT_USAGE, new String[]{"FOUND", "초안 단계에서 사과와 음료 캔의 대각선 구도와 원형 구도를 비교하려고 사용했습니다."}));

        var found = client(reply).findInAnswer(context(answer), answer, AFTER_TOOL, List.of());

        assertEquals(List.of(AI_PURPOSE), found.stream().map(Finding::field).toList());
    }

    // Baseline, unreadable scenario: "Stable Diffusion을 사용했습니다." settled the purpose field.
    @Test void theToolNameAloneIsNotAPurpose() throws IOException {
        var answer = new Answer(7L, AI_TOOL, "Stable Diffusion을 사용했습니다.", TOOL_QUESTION, "Stable Diffusion");
        var reply = findings("answer:7", Map.of(AI_PURPOSE, new String[]{"FOUND", "Stable Diffusion"}));

        assertTrue(client(reply).findInAnswer(context(answer), answer, AFTER_TOOL, List.of()).isEmpty());
    }

    @Test void sayingTheToolWasUsedIsNotAPurpose() throws IOException {
        var answer = new Answer(7L, AI_TOOL, "Stable Diffusion을 사용했습니다.", TOOL_QUESTION, "Stable Diffusion");
        var reply = findings("answer:7", Map.of(AI_PURPOSE, new String[]{"FOUND", "Stable Diffusion을 사용했습니다."}));

        assertTrue(client(reply).findInAnswer(context(answer), answer, AFTER_TOOL, List.of()).isEmpty());
        assertEquals(List.of("InterviewFindings"), schemas);
        assertTrue(LlamaInterviewClient.namesOnlyTheTool("스테이블 디퓨젼 썻어여", List.of("스테이블 디퓨젼")));
        assertFalse(LlamaInterviewClient.namesOnlyTheTool("스테이블 디퓨젼 썻어여. 사과랑 캔 배치 비교용으로만 썻구",
                List.of("스테이블 디퓨젼")));
    }

    @Test void theEditFieldNeedsEditWording() throws IOException {
        var answer = toolAnswer(TYPO_ANSWER);
        var reply = findings("answer:7", Map.of(HUMAN_EDITED_EXPRESSION, new String[]{"FOUND", "사과랑 캔 배치 비교용으로만 썻구"}));

        assertTrue(client(reply).findInAnswer(context(answer), answer, AFTER_TOOL, List.of()).isEmpty());
        assertEquals(List.of("InterviewFindings"), schemas);
    }

    @Test void aSentenceTheIsolatedCheckRejectsSettlesNothing() throws IOException {
        checkAnswer = false;
        var answer = toolAnswer(TYPO_ANSWER);
        var reply = findings("answer:7", Map.of(HUMAN_CREATED_ELEMENT, new String[]{"FOUND", "사과랑 캔 배치 비교용으로만 썻구"}));

        assertTrue(client(reply).findInAnswer(context(answer), answer, AFTER_TOOL, List.of()).isEmpty());
    }

    @Test void aQuoteMissingFromTheAnswerIsRejected() throws IOException {
        var answer = toolAnswer(TYPO_ANSWER);
        var reply = findings("answer:7", Map.of(AI_OUTPUT_USAGE, new String[]{"FOUND", "AI 결과를 배경에 그대로 사용했습니다"}));

        assertTrue(client(reply).findInAnswer(context(answer), answer, AFTER_TOOL, List.of()).isEmpty());
    }

    @Test void answerSearchesNeverReportPartialFindings() throws IOException {
        var answer = toolAnswer(TYPO_ANSWER);
        var reply = findings("answer:7", Map.of(AI_OUTPUT_USAGE, new String[]{"PARTIAL", "생성 이미지는", "어느 부분인지"}));

        assertTrue(client(reply).findInAnswer(context(answer), answer, AFTER_TOOL, List.of()).isEmpty());
    }

    @Test void noOpenFieldMeansNoModelCall() throws IOException {
        var answer = toolAnswer(TYPO_ANSWER);

        assertTrue(client("{}").findInAnswer(context(answer), answer, EnumSet.noneOf(InterviewField.class), List.of()).isEmpty());
        assertTrue(schemas.isEmpty());
    }

    @Test void theArtworkDescriptionSettlesToolAndPurposeAndMarksPartialFields() throws IOException {
        String description = "Midjourney로 사과와 캔의 배치 시안을 여러 개 만들어 구도를 비교한 뒤, 사과 단면과 곡선은 직접 다시 그린 디지털 일러스트입니다.";
        var reply = findings("artwork:3", Map.of(
                AI_TOOL, new String[]{"FOUND", "Midjourney"},
                AI_PURPOSE, new String[]{"FOUND", "Midjourney로 사과와 캔의 배치 시안을 여러 개 만들어 구도를 비교한 뒤"},
                HUMAN_CREATED_ELEMENT, new String[]{"PARTIAL", "사과 단면과 곡선은 직접 다시 그린", "그린 방법"}));

        var found = client(reply).findInInputs(new ProjectInputs(1L, List.of(
                new InputText("artwork:3", "artwork_description", description))));

        assertEquals(List.of(
                new Finding(AI_TOOL, COMPLETED, "Midjourney", "artwork:3", null),
                new Finding(AI_PURPOSE, COMPLETED, "Midjourney로 사과와 캔의 배치 시안을 여러 개 만들어 구도를 비교한 뒤", "artwork:3", null),
                new Finding(HUMAN_CREATED_ELEMENT, NEEDS_REVIEW, "사과 단면과 곡선은 직접 다시 그린", "artwork:3", "그린 방법")), found);
    }

    // Eval run: the project description "…곡선으로 구성한 디지털 일러스트입니다." settled direct creation.
    @Test void aDescriptionOfTheArtworkIsNotTheUsersOwnWork() throws IOException {
        String description = "붉은 사과 단면과 은빛 음료 캔, 여러 색의 곡선으로 구성한 디지털 일러스트입니다.";
        var reply = findings("project:1", Map.of(HUMAN_CREATED_ELEMENT, new String[]{"FOUND", description}));

        assertTrue(client(reply).findInInputs(new ProjectInputs(1L, List.of(
                new InputText("project:1", "project_description", description)))).isEmpty());
        assertEquals(List.of("InterviewFindings"), schemas);
    }

    // Eval run: "not a tool name" for a tool quoted with its particle.
    @Test void aToolNameQuotedWithItsParticleIsKept() throws IOException {
        String description = "Midjourney로 사과와 캔의 배치 시안을 만들었습니다.";
        var reply = findings("artwork:3", Map.of(AI_TOOL, new String[]{"FOUND", "Midjourney로"}));

        var found = client(reply).findInInputs(new ProjectInputs(1L, List.of(
                new InputText("artwork:3", "artwork_description", description))));

        assertEquals(List.of(new Finding(AI_TOOL, COMPLETED, "Midjourney", "artwork:3", null)), found);
    }

    // Eval run: one edit quote spanned the creation sentence and the edit sentence.
    @Test void aQuoteSpanningAnotherFieldsSentenceKeepsOnlyItsOwnSentence() throws IOException {
        String allInOne = "Adobe Firefly를 사용했습니다. 사과 단면은 연필로 윤곽을 잡고 얇은 붓으로 밝은 과육을 직접 그렸습니다. "
                + "캔 뚜껑 테두리는 회색에서 청회색으로 바꾸고 반사광에는 흰색을 덧칠했습니다.";
        var answer = new Answer(7L, AI_TOOL, allInOne, TOOL_QUESTION, "Adobe Firefly");
        String created = "사과 단면은 연필로 윤곽을 잡고 얇은 붓으로 밝은 과육을 직접 그렸습니다.";
        var reply = findings("answer:7", Map.of(
                HUMAN_CREATED_ELEMENT, new String[]{"FOUND", created},
                HUMAN_EDITED_EXPRESSION, new String[]{"FOUND", created + " 캔 뚜껑 테두리는 회색에서 청회색으로 바꾸고 반사광에는 흰색을 덧칠했습니다."}));

        var found = client(reply).findInAnswer(context(answer), answer, AFTER_TOOL, List.of());

        assertEquals(List.of(
                new Finding(HUMAN_CREATED_ELEMENT, COMPLETED, created, "answer:7", null),
                new Finding(HUMAN_EDITED_EXPRESSION, COMPLETED, "캔 뚜껑 테두리는 회색에서 청회색으로 바꾸고 반사광에는 흰색을 덧칠했습니다.",
                        "answer:7", null)), found);
    }

    // Eval run: "생성 이미지는 최종작품에 안 넣엇어요" judged alone did not read as being about AI output.
    @Test void theCheckReadsTheSentenceWithItsAnswerAndQuestion() throws IOException {
        var answer = toolAnswer(TYPO_ANSWER);
        var reply = findings("answer:7", Map.of(AI_OUTPUT_USAGE, new String[]{"FOUND", "생성 이미지는 최종작품에 안 넣엇어요"}));

        client(reply).findInAnswer(context(answer), answer, AFTER_TOOL, List.of());

        assertEquals(TYPO_ANSWER, inputs.get(1).path("context").asString());
        assertEquals(TOOL_QUESTION, inputs.get(1).path("question").asString());
    }

    @Test void aToolQuoteMustNameATool() throws IOException {
        String description = "AI로 사과와 캔의 배치 시안을 여러 개 만들었습니다.";
        var reply = findings("artwork:3", Map.of(AI_TOOL, new String[]{"FOUND", description}));

        assertTrue(client(reply).findInInputs(new ProjectInputs(1L, List.of(
                new InputText("artwork:3", "artwork_description", description)))).isEmpty());
    }
}
