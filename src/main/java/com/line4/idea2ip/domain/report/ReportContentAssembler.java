package com.line4.idea2ip.domain.report;

import com.line4.idea2ip.domain.contribution.ContributionModels.Confirmed;
import com.line4.idea2ip.domain.report.ReportModels.*;
import com.line4.idea2ip.integration.ai.BriefAiClient.*;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
class ReportContentAssembler {
    static final String NO_OFFICIAL_GUIDE =
            "공식 지침: 검토된 공식 판본이 연결되지 않았습니다. 등록 신청 전 한국저작권위원회 등 공식 안내를 직접 확인해 주세요.";

    Content assemble(InputSnapshot input, Draft draft, Content base, InputSnapshot baseInput) {
        Map<String, Evidence> sources = new LinkedHashMap<>();
        for (var source : draft.sources()) sources.put(source.sourceId(), source);
        for (var source : input.aiInput().sources()) sources.putIfAbsent(source.sourceId(), Evidence.from(source, null));

        Map<String, Section> byKey = new LinkedHashMap<>();
        draft.sections().forEach(section -> byKey.put(section.sectionKey(), section));
        var profile = input.profile();
        Map<Long, MaterialFact> facts = new HashMap<>();
        if (profile != null && profile.materials() != null) profile.materials().forEach(fact -> facts.put(fact.materialId(), fact));
        List<String> human = new ArrayList<>();
        // The interview asks tool, purpose and final use once per project, but the answers can land on
        // different contribution steps; they describe one AI use and are shown as one block.
        Set<String> aiTools = new LinkedHashSet<>();
        Set<String> aiPurposes = new LinkedHashSet<>();
        Set<String> aiOutputs = new LinkedHashSet<>();
        Map<Long, MaterialFact> aiMaterials = new LinkedHashMap<>();
        boolean anyAi = false;
        Set<String> aiSourceIds = new LinkedHashSet<>();
        Set<String> humanSourceIds = new LinkedHashSet<>();
        for (var step : input.contributions()) {
            var fact = materialOf(step, facts);
            String header = stepHeader(step, fact);
            var humanLines = new ArrayList<String>();
            step.content().humanCreatedElements().forEach(text -> humanLines.add("직접 창작: " + text));
            step.content().humanEdits().forEach(text -> humanLines.add("직접 수정: " + text));
            if (!humanLines.isEmpty()) human.add(paragraph(header, humanLines));
            var usage = step.content().aiUsage();
            if (usage != null) {
                anyAi = true;
                var aiTexts = new ArrayList<String>();
                if (usage.tools() != null) usage.tools().stream().filter(ReportContentAssembler::hasText).forEach(tool -> {
                    aiTools.add(tool.strip());
                    aiTexts.add(tool);
                });
                if (hasText(usage.purpose())) {
                    aiPurposes.add(usage.purpose().strip());
                    aiTexts.add(usage.purpose());
                }
                if (hasText(usage.outputUsage())) {
                    aiOutputs.add(usage.outputUsage().strip());
                    aiTexts.add(usage.outputUsage());
                }
                if (fact != null) aiMaterials.putIfAbsent(fact.materialId(), fact);
                aiSourceIds.addAll(relevantSourceIds(step.sources(), aiTexts));
            }
            var humanTexts = new ArrayList<>(step.content().humanCreatedElements());
            humanTexts.addAll(step.content().humanEdits());
            if (!humanTexts.isEmpty()) humanSourceIds.addAll(relevantSourceIds(step.sources(), humanTexts));
            for (var evidence : step.sources()) {
                sources.merge(evidence.sourceId(), evidence,
                        (known, confirmed) -> known.quote() == null && confirmed.quote() != null ? confirmed : known);
            }
        }
        if (human.isEmpty()) human.add(input.noContributionReason() == null
                ? "확정된 직접 창작·수정 내용이 없습니다." : "사용자 확정: 창작 기여 없음 — " + input.noContributionReason());
        byKey.put(SectionKey.HUMAN_CONTRIBUTION.name(), section(SectionKey.HUMAN_CONTRIBUTION, human,
                List.copyOf(humanSourceIds)));

        if (anyAi) {
            byKey.put(SectionKey.AI_USAGE.name(), section(SectionKey.AI_USAGE,
                    List.of(aiParagraph(aiTools, aiPurposes, aiOutputs, aiMaterials.values())), List.copyOf(aiSourceIds)));
        }

        var artwork = input.aiInput().sources().stream().filter(source -> source.artworkId() != null).toList();
        var aiOverview = byKey.get(SectionKey.WORK_OVERVIEW.name());
        if (profile != null || !artwork.isEmpty()) {
            var overview = new ArrayList<String>();
            if (profile != null) {
                overview.add("작품명: " + input.aiInput().title());
                overview.add("작품 유형: " + orUnknown(SourceLabels.artworkType(profile.artworkType())));
                overview.add("주제·기획 의도: " + orUnknown(profile.projectDescription()));
                if (profile.artworkTitle() != null) {
                    overview.add("최종 작품: " + profile.artworkTitle());
                    overview.add("최종 작품 설명: " + orUnknown(profile.artworkDescription()));
                }
            } else {
                overview.addAll(artwork.stream().map(Source::content).toList());
            }
            var ids = new LinkedHashSet<>(artwork.stream().map(Source::sourceId).toList());
            if (aiOverview != null && !aiOverview.sourceIds().isEmpty()) {
                overview.addAll(aiOverview.paragraphs());
                ids.addAll(aiOverview.sourceIds());
            }
            byKey.put(SectionKey.WORK_OVERVIEW.name(), section(SectionKey.WORK_OVERVIEW, overview, List.copyOf(ids)));
        }

        var evidenceIds = sources.values().stream().filter(source -> !"OFFICIAL_GUIDE".equals(source.kind()))
                .map(Evidence::sourceId).toList();
        var references = profile == null
                ? sources.values().stream().filter(source -> !"OFFICIAL_GUIDE".equals(source.kind()))
                        .map(source -> SourceLabels.kind(source.kind()) + " · " + SourceLabels.title(source)).toList()
                : references(profile, input.contributions(), sources.values());
        byKey.put(SectionKey.EVIDENCE_SUMMARY.name(), section(SectionKey.EVIDENCE_SUMMARY, references, evidenceIds));

        var guidance = byKey.get(SectionKey.GAPS_AND_GUIDANCE.name());
        // Numbered to-do items first; official guidance status is listed separately below them.
        var gaps = new ArrayList<String>();
        var todo = input.warnings();
        for (int i = 0; i < todo.size(); i++) {
            gaps.add(todo.size() == 1 && todo.getFirst().equals(ReportChecklist.NOTHING_MISSING)
                    ? todo.getFirst() : (i + 1) + ". " + todo.get(i));
        }
        if (guidance != null) gaps.addAll(guidance.paragraphs());
        if (sources.values().stream().noneMatch(source -> "OFFICIAL_GUIDE".equals(source.kind()))) {
            gaps.add(NO_OFFICIAL_GUIDE);
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
                // When what a section is built from changed, show the new generated content and keep the user's
                // edit beside it for review, instead of letting the old edit hide the update.
                if (derivedInputsChanged(previous.sectionKey(), baseInput, input)) {
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

    private static final List<String> STAGES = List.of("INITIAL_SKETCH", "AI_ASSISTED", "MANUAL_EDIT");

    /** Materials in production order (date, then stage, then the user's order), then the final artwork. */
    private static List<String> references(Profile profile, List<Confirmed> steps, Collection<Evidence> sources) {
        var ordered = new ArrayList<>(profile.materials() == null ? List.<MaterialFact>of() : profile.materials());
        ordered.sort(Comparator.comparing(MaterialFact::productionDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(fact -> STAGES.indexOf(fact.type()) < 0 ? STAGES.size() : STAGES.indexOf(fact.type()))
                .thenComparingInt(MaterialFact::order));
        var lines = new ArrayList<String>();
        for (var fact : ordered) {
            var entry = new ArrayList<String>();
            entry.add((lines.size() + 1) + ". [" + SourceLabels.materialType(fact.type()) + "] " + fact.title());
            entry.add("제작일: " + (fact.productionDate() == null ? "미입력" : fact.productionDate())
                    + " · 첨부 파일 " + fact.fileCount() + "개");
            if (hasText(fact.description())) entry.add("설명: " + excerpt(fact.description(), 300));
            if (hasText(fact.usedAi())) entry.add("사용 도구: " + fact.usedAi());
            var work = linkedWork(fact.materialId(), steps);
            if (!work.isEmpty()) entry.add("연결된 작업: " + String.join(", ", work));
            lines.add(String.join("\n", entry));
        }
        if (profile.artworkTitle() != null) lines.add((lines.size() + 1) + ". [최종 작품] " + profile.artworkTitle());
        long answers = sources.stream().filter(source -> "USER_STATEMENT".equals(source.kind())).count();
        if (answers > 0) lines.add("인터뷰 답변 " + answers + "건 (사용자 진술) — 아래 출처 세부 정보 참고");
        if (lines.isEmpty()) lines.add("등록된 창작 과정 자료가 없습니다.");
        return lines;
    }

    /** One AI use: related materials, then tool, output, purpose and final use; "미확인" only when nobody answered. */
    private static String aiParagraph(Set<String> tools, Set<String> purposes, Set<String> outputs,
                                      Collection<MaterialFact> materials) {
        var lines = new ArrayList<String>();
        var related = materials.stream().map(fact -> fact.title() + " (" + SourceLabels.materialType(fact.type())
                + (fact.productionDate() == null ? "" : " · " + fact.productionDate()) + ")").toList();
        if (!related.isEmpty()) lines.add("관련 자료: " + String.join(", ", related));
        lines.add("도구: " + (tools.isEmpty() ? "미확인" : String.join(", ", tools)));
        // The material description is the user's own account of what the AI produced.
        var outputsDescribed = materials.stream()
                .filter(fact -> "AI_ASSISTED".equals(fact.type()) && hasText(fact.description())).toList();
        for (var fact : outputsDescribed) {
            lines.add("산출물 설명" + (outputsDescribed.size() > 1 ? " (" + fact.title() + ")" : "") + ": "
                    + excerpt(fact.description(), 300));
        }
        if (purposes.isEmpty()) lines.add("사용 목적: 미확인");
        else purposes.forEach(purpose -> lines.add("사용 목적: " + purpose));
        if (outputs.isEmpty()) lines.add("최종 작품 반영: 미확인");
        else outputs.forEach(output -> lines.add("최종 작품 반영: " + output));
        return String.join("\n", lines);
    }

    private static List<String> linkedWork(Long materialId, List<Confirmed> steps) {
        var work = new ArrayList<String>();
        int created = 0, edited = 0;
        boolean ai = false;
        for (var step : steps) {
            if (step.sources().stream().noneMatch(source -> source.sourceId().equals("material:" + materialId))) continue;
            created += step.content().humanCreatedElements().size();
            edited += step.content().humanEdits().size();
            ai |= step.content().aiUsage() != null;
        }
        if (ai) work.add("AI 활용");
        if (created > 0) work.add("직접 창작 " + created + "건");
        if (edited > 0) work.add("직접 수정 " + edited + "건");
        return work;
    }

    private static MaterialFact materialOf(Confirmed step, Map<Long, MaterialFact> facts) {
        return step.sources().stream().map(Evidence::sourceId).filter(id -> id.matches("material:\\d+"))
                .map(id -> facts.get(Long.valueOf(id.substring("material:".length()))))
                .filter(Objects::nonNull).findFirst().orElse(null);
    }

    /** "구도 자료 (AI 산출물 · 2026-11-01)"; null for interview-only steps whose titles are internal. */
    private static String stepHeader(Confirmed step, MaterialFact fact) {
        if (SourceLabels.isInterviewTitle(step.title())) return null;
        if (fact == null) return step.title();
        return step.title() + " (" + SourceLabels.materialType(fact.type())
                + (fact.productionDate() == null ? "" : " · " + fact.productionDate()) + ")";
    }

    private static String paragraph(String header, List<String> lines) {
        var all = new ArrayList<String>();
        if (header != null) all.add(header);
        all.addAll(lines);
        return String.join("\n", all);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String orUnknown(String value) {
        return hasText(value) ? value.strip() : "미확인";
    }

    private static String excerpt(String value, int max) {
        String text = value.strip();
        if (text.codePointCount(0, text.length()) <= max) return text;
        return text.substring(0, text.offsetByCodePoints(0, max)) + "…";
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

    /**
     * Inputs a section is generated from beyond its cited sources: confirmed contributions for human work,
     * stored materials and contributions for the reference list, and the rule-based checklist for gaps. A changed
     * production date, for example, alters the checklist without touching any cited source.
     */
    private static boolean derivedInputsChanged(String sectionKey, InputSnapshot baseInput, InputSnapshot input) {
        if (baseInput == null) return false;
        boolean contributionsChanged = !baseInput.contributions().equals(input.contributions())
                || !Objects.equals(baseInput.noContributionReason(), input.noContributionReason());
        return switch (SectionKey.valueOf(sectionKey)) {
            case HUMAN_CONTRIBUTION -> contributionsChanged;
            case EVIDENCE_SUMMARY -> contributionsChanged || !Objects.equals(baseInput.profile(), input.profile())
                    || !baseInput.aiInput().sources().equals(input.aiInput().sources());
            case GAPS_AND_GUIDANCE -> !Objects.equals(baseInput.warnings(), input.warnings());
            case WORK_OVERVIEW, AI_USAGE -> false;
        };
    }

    private boolean evidenceChanged(Section previous, Section generated, Content base, InputSnapshot baseInput,
                                    InputSnapshot input, Map<String, Evidence> current) {
        if (generated == null || !Set.copyOf(previous.sourceIds()).equals(Set.copyOf(generated.sourceIds()))) return true;
        if (derivedInputsChanged(previous.sectionKey(), baseInput, input)) return true;
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
