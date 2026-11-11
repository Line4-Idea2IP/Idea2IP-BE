package com.line4.idea2ip.integration.ai;

import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FastApiBriefAiClientTests {
    @Test void officialMetadataAndProjectSnapshotAreValidated() throws Exception {
        var legal = new LinkedHashMap<String, Object>();
        legal.put("source_id", "test-only-law"); legal.put("kind", "OFFICIAL_GUIDE");
        legal.put("title", "가상 테스트 지침 — 실제 공식 자료 아님"); legal.put("publisher", "가상 발행기관");
        legal.put("edition", "TEST-ONLY"); legal.put("jurisdiction", "KR"); legal.put("reviewed", true);
        legal.put("official_url", "https://www.copyright.or.kr/test-only");
        legal.put("published_on", "2026-01-01"); legal.put("retrieved_on", "2026-01-02"); legal.put("effective_from", "2026-01-01");
        legal.put("content", "가상 테스트: 실제 법률 안내가 아닙니다.");
        legal.put("sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(((String) legal.get("content")).getBytes(StandardCharsets.UTF_8))));
        var sections = new LinkedHashMap<String, Object>();
        for (String key : List.of("work_overview", "ai_usage", "human_contribution", "evidence_summary", "gaps_and_guidance")) {
            sections.put(key, Map.of("title", "테스트", "statements", List.of("테스트"), "source_ids", key.equals("gaps_and_guidance") ? List.of("test-only-law") : List.of()));
        }
        var response = new LinkedHashMap<String, Object>(Map.of("project_id", "test-project", "snapshot_version", "snapshot-1", "mode", "live", "status", "DRAFT", "schema_version", "1.0", "sections", sections, "sources", List.of(legal)));
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] bytes = JsonMapper.builder().build().writeValueAsBytes(response);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        server.start();
        try {
            var client = new FastApiBriefAiClient("http://127.0.0.1:" + server.getAddress().getPort(), "");
            var input = new BriefAiClient.Input("test-project", "가상 작품", "snapshot-1", List.of());
            var accepted = client.brief(input).sources().getFirst();
            assertEquals("가상 발행기관", accepted.publisher()); assertEquals("TEST-ONLY", accepted.edition());
            assertEquals("2026-01-01", accepted.effectiveFrom()); assertEquals(legal.get("official_url"), accepted.officialUrl());
            for (var change : Map.of("reviewed", false, "official_url", "https://copyright.or.kr.attacker.example/test", "sha256", "0".repeat(64), "effective_from", "2099-01-01").entrySet()) {
                Object original = legal.put(change.getKey(), change.getValue());
                assertThrows(BriefAiClient.Failure.class, () -> client.brief(input));
                legal.put(change.getKey(), original);
            }
            response.put("snapshot_version", "other-snapshot");
            assertThrows(BriefAiClient.Failure.class, () -> client.brief(input));
            response.put("snapshot_version", "snapshot-1");
            response.put("sources", List.of(Map.of("source_id", "foreign-material", "kind", "ATTACHED_FILE", "project_id", "other-project", "content", "외부 자료")));
            assertThrows(BriefAiClient.Failure.class, () -> client.brief(input));
        } finally { server.stop(0); }
    }
}
