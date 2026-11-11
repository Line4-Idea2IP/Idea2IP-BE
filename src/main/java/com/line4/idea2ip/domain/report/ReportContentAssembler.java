package com.line4.idea2ip.domain.report;

import com.line4.idea2ip.domain.report.ReportModels.*;
import com.line4.idea2ip.integration.ai.BriefAiClient.*;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
class ReportContentAssembler {
    Content assemble(InputSnapshot input, Draft draft, Content base, InputSnapshot baseInput) {
        Map<String, Evidence> sources = new LinkedHashMap<>();
        for (var source : draft.sources()) sources.put(source.sourceId(), source);
        for (var source : input.aiInput().sources()) sources.putIfAbsent(source.sourceId(), Evidence.from(source, null));

        Map<String, Section> byKey = new LinkedHashMap<>();
        draft.sections().forEach(section -> byKey.put(section.sectionKey(), section));
        List<String> human = new ArrayList<>();
        List<String> confirmedAi = new ArrayList<>();
        Set<String> aiSourceIds = new LinkedHashSet<>();
        Set<String> humanSourceIds = new LinkedHashSet<>();
        for (var step : input.contributions()) {
            human.addAll(step.content().humanCreatedElements().stream().map(text -> step.title() + " · 직접 창작: " + text).toList());
            human.addAll(step.content().humanEdits().stream().map(text -> step.title() + " · 직접 수정: " + text).toList());
            var usage = step.content().aiUsage();
            if (usage != null) {
                var aiTexts = new ArrayList<String>();
                if (usage.tools() != null) aiTexts.addAll(usage.tools());
                if (usage.purpose() != null) aiTexts.add(usage.purpose());
                if (usage.outputUsage() != null) aiTexts.add(usage.outputUsage());
                if (usage.tools() != null) confirmedAi.addAll(usage.tools().stream().map(tool -> "도구: " + tool).toList());
                if (usage.purpose() != null) confirmedAi.add("목적: " + usage.purpose());
                if (usage.outputUsage() != null) confirmedAi.add("산출물 활용: " + usage.outputUsage());
                aiSourceIds.addAll(relevantSourceIds(step.sources(), aiTexts));
            }
            var humanTexts = new ArrayList<>(step.content().humanCreatedElements());
            humanTexts.addAll(step.content().humanEdits());
            if (!humanTexts.isEmpty()) humanSourceIds.addAll(relevantSourceIds(step.sources(), humanTexts));
            for (var evidence : step.sources()) sources.putIfAbsent(evidence.sourceId(), evidence);
        }
        if (human.isEmpty()) human.add(input.noContributionReason() == null
                ? "확정된 직접 창작·수정 내용이 없습니다." : "사용자 확정: 창작 기여 없음 — " + input.noContributionReason());
        byKey.put(SectionKey.HUMAN_CONTRIBUTION.name(), section(SectionKey.HUMAN_CONTRIBUTION, human,
                List.copyOf(humanSourceIds)));

        if (!confirmedAi.isEmpty()) {
            byKey.put(SectionKey.AI_USAGE.name(), section(SectionKey.AI_USAGE, confirmedAi, List.copyOf(aiSourceIds)));
        }

        var artwork = input.aiInput().sources().stream().filter(source -> source.artworkId() != null).toList();
        if (!artwork.isEmpty()) {
            var overview = new ArrayList<>(artwork.stream().map(Source::content).toList());
            var ids = new LinkedHashSet<>(artwork.stream().map(Source::sourceId).toList());
            var aiOverview = byKey.get(SectionKey.WORK_OVERVIEW.name());
            if (aiOverview != null && !aiOverview.sourceIds().isEmpty()) {
                overview.addAll(aiOverview.paragraphs());
                ids.addAll(aiOverview.sourceIds());
            }
            byKey.put(SectionKey.WORK_OVERVIEW.name(), section(SectionKey.WORK_OVERVIEW, overview, List.copyOf(ids)));
        }

        var summary = section(SectionKey.EVIDENCE_SUMMARY, sources.values().stream()
                .filter(source -> !"OFFICIAL_GUIDE".equals(source.kind()))
                .map(source -> source.title()).toList(), sources.values().stream()
                .filter(source -> !"OFFICIAL_GUIDE".equals(source.kind())).map(Evidence::sourceId).toList());
        byKey.put(SectionKey.EVIDENCE_SUMMARY.name(), summary);

        var guidance = byKey.get(SectionKey.GAPS_AND_GUIDANCE.name());
        var gaps = new ArrayList<>(input.warnings());
        if (guidance != null) gaps.addAll(guidance.paragraphs());
        if (sources.values().stream().noneMatch(source -> "OFFICIAL_GUIDE".equals(source.kind()))) {
            gaps.add("검토된 공식 지침을 확인할 수 없습니다.");
        }
        byKey.put(SectionKey.GAPS_AND_GUIDANCE.name(), section(SectionKey.GAPS_AND_GUIDANCE,
                gaps, guidance == null ? List.of() : guidance.sourceIds()));

        var flags = new ArrayList<Flag>();
        var editedKeys = base == null ? List.<String>of() : base.editedSectionKeys();
        if (base != null) {
            for (Section previous : base.sections()) {
                if (!editedKeys.contains(previous.sectionKey())) continue;
                var generated = byKey.get(previous.sectionKey());
                var carriedFlag = base.reviewFlags().stream()
                        .filter(flag -> flag.sectionKey().equals(previous.sectionKey())).findFirst().orElse(null);
                var retained = carriedFlag == null ? List.<String>of() : carriedFlag.retainedUserParagraphs();
                if (previous.sectionKey().equals(SectionKey.HUMAN_CONTRIBUTION.name())
                        && (!baseInput.contributions().equals(input.contributions())
                        || !Objects.equals(baseInput.noContributionReason(), input.noContributionReason()))) {
                    retained = retained.isEmpty() ? previous.paragraphs() : retained;
                }
                if (carriedFlag != null || evidenceChanged(previous, generated, base, baseInput, input, sources)) {
                    flags.add(new Flag(previous.sectionKey(), "EDITED_SECTION_EVIDENCE_CHANGED", previous.sourceIds(), retained));
                }
                // Preserve edits for review without bringing back text from superseded contribution decisions.
                if (!retained.isEmpty()) continue;
                var retainedIds = previous.sourceIds().stream().filter(sources::containsKey).toList();
                byKey.put(previous.sectionKey(), new Section(previous.sectionKey(), previous.title(),
                        previous.paragraphs(), retainedIds));
            }
        }
        var sections = Arrays.stream(SectionKey.values()).map(key -> {
            var content = byKey.get(key.name());
            return content == null ? section(key, List.of("확인할 정보가 없습니다."), List.of())
                    : section(key, content.paragraphs(), content.sourceIds());
        }).toList();
        return new Content(draft.schemaVersion(), draft.mode(), sections, List.copyOf(sources.values()),
                editedKeys, List.copyOf(flags));
    }

    Content edit(Content base, List<SectionEdit> edits) {
        Map<String, SectionEdit> byKey = new HashMap<>();
        edits.forEach(edit -> byKey.put(edit.sectionKey().name(), edit));
        var editedKeys = new LinkedHashSet<>(base.editedSectionKeys());
        editedKeys.addAll(edits.stream().map(edit -> edit.sectionKey().name()).toList());
        var sections = base.sections().stream().map(section -> {
            var edit = byKey.get(section.sectionKey());
            return new Section(section.sectionKey(), SectionKey.valueOf(section.sectionKey()).title(),
                    edit == null ? section.paragraphs() : edit.paragraphs(), section.sourceIds());
        }).toList();
        return new Content(base.schemaVersion(), base.mode(), sections, base.sources(), List.copyOf(editedKeys),
                base.reviewFlags().stream().filter(flag -> !byKey.containsKey(flag.sectionKey())).toList());
    }

    private boolean evidenceChanged(Section previous, Section generated, Content base, InputSnapshot baseInput,
                                    InputSnapshot input, Map<String, Evidence> current) {
        if (generated == null || !Set.copyOf(previous.sourceIds()).equals(Set.copyOf(generated.sourceIds()))) return true;
        if (previous.sectionKey().equals(SectionKey.HUMAN_CONTRIBUTION.name())
                && (!baseInput.contributions().equals(input.contributions())
                        || !Objects.equals(baseInput.noContributionReason(), input.noContributionReason()))) return true;
        var oldSources = base.sources().stream().filter(source -> previous.sourceIds().contains(source.sourceId())).toList();
        if (oldSources.stream().anyMatch(source -> !source.equals(current.get(source.sourceId())))) return true;
        var priorInput = baseInput.aiInput().sources().stream()
                .filter(source -> previous.sourceIds().contains(source.sourceId())).toList();
        return !input.aiInput().sources().containsAll(priorInput);
    }

    /**
     * Interview answers whose quoted text appears in the section's confirmed statements, plus the step's
     * unquoted anchors (material/artwork). Falls back to every step source when the user's edits removed
     * all quoted text, so a section never loses its evidence.
     */
    private static List<String> relevantSourceIds(List<Evidence> stepSources, List<String> texts) {
        var quoted = stepSources.stream().filter(source -> source.quote() != null
                && texts.stream().anyMatch(text -> text.contains(source.quote()))).toList();
        if (quoted.isEmpty()) return stepSources.stream().map(Evidence::sourceId).toList();
        return stepSources.stream().filter(source -> source.quote() == null || quoted.contains(source))
                .map(Evidence::sourceId).toList();
    }

    private Section section(SectionKey key, List<String> paragraphs, List<String> ids) {
        return new Section(key.name(), key.title(), List.copyOf(paragraphs), ids);
    }
}
