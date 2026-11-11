package com.line4.idea2ip.integration.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "idea2ip.brief.ai-mode", havingValue = "live")
class FastApiBriefAiClient implements BriefAiClient {
    private final RestClient client;

    FastApiBriefAiClient(@Value("${idea2ip.brief.ai-base-url}") String baseUrl,
                        @Value("${idea2ip.brief.ai-token}") String token) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(180));
        var builder = RestClient.builder().baseUrl(baseUrl).requestFactory(factory);
        if (!token.isBlank()) {
            builder.defaultHeader("X-Service-Token", token);
        }
        client = builder.build();
    }

    @Override
    public CandidateResult contributions(Input input) {
        JsonNode result = post(input, "contributions");
        Map<String, Source> sources = byId(input);
        List<Candidate> candidates = new ArrayList<>();
        for (JsonNode node : array(result, "contributions")) {
            String category = required(node, "category");
            String statement = required(node, "statement");
            List<String> ids = strings(node, "source_ids");
            if (!Set.of("human_edit", "selection_arrangement", "creative_intent").contains(category)
                    || ids.isEmpty() || ids.stream().anyMatch(id -> !sources.containsKey(id)
                    || sources.get(id).messageId() == null || !sources.get(id).content().contains(statement))) {
                throw invalid();
            }
            candidates.add(new Candidate(category, statement, ids));
        }
        return new CandidateResult(List.copyOf(candidates), strings(result, "unresolved_fields"));
    }

    @Override
    public Draft brief(Input input) {
        JsonNode result = post(input, "brief");
        if (!"1.0".equals(required(result, "schema_version")) || !"DRAFT".equals(required(result, "status"))) {
            throw invalid();
        }
        Map<String, Source> supplied = byId(input);
        Map<String, Evidence> accepted = new LinkedHashMap<>();
        for (JsonNode node : array(result, "sources")) {
            String id = required(node, "source_id");
            Evidence evidence;
            Source original = supplied.get(id);
            if (original != null) {
                if (!input.projectId().equals(required(node, "project_id"))
                        || !original.kind().equals(required(node, "kind"))
                        || !original.content().strip().equals(required(node, "content"))) {
                    throw invalid();
                }
                evidence = Evidence.from(original, null);
            } else if ("OFFICIAL_GUIDE".equals(required(node, "kind")) && node.path("reviewed").asBoolean(false)) {
                evidence = new Evidence(id, "OFFICIAL_GUIDE", required(node, "title"), required(node, "content"),
                        null, required(node, "official_url"), required(node, "edition"), required(node, "effective_from"),
                        optional(node, "effective_to"), required(node, "publisher"), optional(node, "published_on"),
                        optional(node, "retrieved_on"));
            } else {
                throw invalid();
            }
            if (accepted.putIfAbsent(id, evidence) != null) {
                throw invalid();
            }
        }
        var sections = new ArrayList<Section>();
        for (String key : List.of("work_overview", "ai_usage", "human_contribution", "evidence_summary", "gaps_and_guidance")) {
            JsonNode item = result.path("sections").path(key);
            List<String> ids = strings(item, "source_ids");
            if (!accepted.keySet().containsAll(ids)) {
                throw invalid();
            }
            var paragraphs = strings(item, "statements");
            // The v1 gaps contain questions derived from the unreviewed interview. Keep reviewed guides here.
            if (key.equals("gaps_and_guidance")) {
                paragraphs = ids.stream().map(accepted::get).filter(e -> e.officialUrl() != null)
                        .map(Evidence::quote).toList();
            }
            sections.add(new Section(key.toUpperCase(java.util.Locale.ROOT), required(item, "title"), paragraphs, ids));
        }
        return new Draft("1.0", "live", List.copyOf(sections), List.copyOf(accepted.values()));
    }

    private JsonNode post(Input input, String operation) {
        if (input.sources().size() > 100 || input.sources().stream().mapToLong(source -> source.content().length()).sum() > 100_000
                || input.sources().stream().anyMatch(source -> source.content().length() > 8000)) {
            throw new Failure("AI_INPUT_INVALID", false);
        }
        JsonNode result;
        try {
            result = client.post().uri("/v1/projects/{id}/{operation}", input.projectId(), operation)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("project_id", input.projectId(), "title", title(input.title()),
                            "snapshot_version", input.snapshotVersion(),
                            "sources", input.sources().stream().map(source -> requestSource(input, source)).toList()))
                    .retrieve().body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 422 || status == 401) {
                throw new Failure("AI_INPUT_INVALID", false);
            }
            throw new Failure(status == 502 ? "AI_RESULT_INVALID" : "AI_UNAVAILABLE", true);
        } catch (RestClientException exception) {
            throw new Failure("AI_UNAVAILABLE", true);
        }
        if (result == null || !input.projectId().equals(required(result, "project_id"))
                || !input.snapshotVersion().equals(required(result, "snapshot_version"))
                || !"live".equals(required(result, "mode"))) {
            throw invalid();
        }
        return result;
    }

    private Map<String, String> requestSource(Input input, Source source) {
        var data = new LinkedHashMap<String, String>();
        data.put("source_id", source.sourceId());
        data.put("project_id", input.projectId());
        data.put("kind", source.kind());
        data.put("title", title(source.title()));
        data.put("content", source.content());
        if (source.targetField() != null) data.put("target_field", source.targetField());
        if (source.locator() != null) data.put("locator", source.locator());
        return data;
    }

    private String title(String value) {
        int end = value.offsetByCodePoints(0, Math.min(200, value.codePointCount(0, value.length())));
        return value.substring(0, end).strip();
    }

    private Map<String, Source> byId(Input input) {
        return input.sources().stream().collect(Collectors.toMap(Source::sourceId, source -> source));
    }

    private JsonNode array(JsonNode node, String key) {
        JsonNode value = node.path(key);
        if (!value.isArray()) throw invalid();
        return value;
    }

    private List<String> strings(JsonNode node, String key) {
        List<String> values = new ArrayList<>();
        for (JsonNode value : array(node, key)) {
            if (!value.isTextual()) throw invalid();
            values.add(value.asString());
        }
        if (key.equals("source_ids") && new HashSet<>(values).size() != values.size()) throw invalid();
        return List.copyOf(values);
    }

    private String required(JsonNode node, String key) {
        JsonNode value = node.path(key);
        if (!value.isTextual() || value.asString().isBlank()) throw invalid();
        return value.asString();
    }

    private String optional(JsonNode node, String key) {
        return node.path(key).isTextual() ? node.path(key).asString() : null;
    }

    private Failure invalid() {
        return new Failure("AI_RESULT_INVALID", true);
    }
}
