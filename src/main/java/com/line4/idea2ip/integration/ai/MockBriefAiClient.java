package com.line4.idea2ip.integration.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "idea2ip.brief.ai-mode", havingValue = "mock", matchIfMissing = true)
class MockBriefAiClient implements BriefAiClient {
    @Override
    public CandidateResult contributions(Input input) {
        var candidates = input.sources().stream().filter(source -> source.messageId() != null)
                .filter(source -> "human_edit".equals(source.targetField()) || source.targetField() == null)
                .filter(source -> !java.util.Set.of("없음", "없어요", "없습니다", "해당 없음")
                        .contains(source.content().replaceFirst("[.]$", "").strip()))
                .map(source -> new Candidate(source.targetField() == null ? "human_created" : "human_edit",
                        source.content(), List.of(source.sourceId())))
                .toList();
        var unresolved = java.util.stream.Stream.of("ai_usage", "human_edit")
                .filter(field -> input.sources().stream().noneMatch(source -> field.equals(source.targetField())))
                .toList();
        return new CandidateResult(candidates, unresolved);
    }

    @Override
    public Draft brief(Input input) {
        var artwork = input.sources().stream().filter(source -> source.artworkId() != null).toList();
        var ai = input.sources().stream().filter(source -> "ai_usage".equals(source.targetField())).toList();
        return new Draft("1.0", "mock", List.of(
                section("WORK_OVERVIEW", "작품 개요", artwork),
                section("AI_USAGE", "AI 활용", ai),
                new Section("GAPS_AND_GUIDANCE", "보완 사항·공식 지침",
                        List.of("모의 생성 결과입니다. 검토된 공식 지침 연결이 필요합니다."), List.of())
        ), input.sources().stream().map(source -> Evidence.from(source, null)).toList());
    }

    private Section section(String key, String title, List<Source> sources) {
        return new Section(key, title, sources.isEmpty() ? List.of("확인할 자료가 없습니다.")
                : sources.stream().map(Source::content).toList(), sources.stream().map(Source::sourceId).toList());
    }
}
