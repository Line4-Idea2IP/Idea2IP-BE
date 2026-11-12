package com.line4.idea2ip.domain.report;

import com.line4.idea2ip.domain.contribution.ContributionModels.AiUsage;
import com.line4.idea2ip.domain.contribution.ContributionModels.Confirmed;
import com.line4.idea2ip.domain.contribution.ContributionModels.Content;
import com.line4.idea2ip.domain.interview.entity.InterviewField;
import com.line4.idea2ip.domain.interview.entity.InterviewFieldState.Status;
import com.line4.idea2ip.domain.material.entity.MaterialType;
import com.line4.idea2ip.domain.report.ReportModels.FieldState;
import com.line4.idea2ip.domain.report.ReportModels.ReadinessSection;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ReportReadinessSectionsTests {
    private static FieldState state(List<ReadinessSection> sections, String section, String field) {
        return sections.stream().filter(s -> s.sectionKey().equals(section)).findFirst().orElseThrow()
                .fields().stream().filter(f -> f.key().equals(field)).findFirst().orElseThrow().state();
    }

    @Test void writtenAndUnverifiedFieldsFollowStoredData() {
        var steps = List.of(new Confirmed("구도 자료", new Content(new AiUsage(List.of("Stable Diffusion"), "구도 비교", null),
                List.of(), List.of("시계판을 칠했습니다.")), List.of()));
        var sections = ReportReadinessSections.sections(new ReportReadinessSections.Inputs("DIGITAL_ILLUSTRATION", " ",
                true, "완성작", steps, null, Map.of(), Set.of(MaterialType.AI_ASSISTED, MaterialType.MANUAL_EDIT)));

        assertEquals(List.of("WORK_OVERVIEW", "AI_USAGE", "HUMAN_CONTRIBUTION", "EVIDENCE_SUMMARY"),
                sections.stream().map(ReadinessSection::sectionKey).toList());
        assertEquals(FieldState.UNVERIFIED, state(sections, "WORK_OVERVIEW", "projectDescription"));
        assertEquals(FieldState.WRITTEN, state(sections, "AI_USAGE", "tool"));
        assertEquals(FieldState.UNVERIFIED, state(sections, "AI_USAGE", "outputUsage"));
        assertEquals(FieldState.WRITTEN, state(sections, "HUMAN_CONTRIBUTION", "humanEdited"));
        assertEquals(FieldState.UNVERIFIED, state(sections, "EVIDENCE_SUMMARY", "initialSketch"));
        assertFalse(sections.get(1).written());
        assertEquals(2, sections.get(1).writtenCount());
        assertEquals(3, sections.get(1).totalCount());

        var summary = ReportReadinessSections.summary(sections);
        assertEquals(12, summary.total());
        assertEquals(summary.total(), summary.written() + summary.notApplicable() + summary.unverified());
    }

    @Test void noAiAnswerAndNoContributionDecisionCountAsNotApplicable() {
        var sections = ReportReadinessSections.sections(new ReportReadinessSections.Inputs(null, null, true, null,
                List.of(), "직접 작업 없음", Map.of(InterviewField.AI_TOOL, Status.NOT_APPLICABLE), Set.of()));
        assertEquals(FieldState.NOT_APPLICABLE, state(sections, "AI_USAGE", "tool"));
        assertEquals(FieldState.NOT_APPLICABLE, state(sections, "AI_USAGE", "purpose"));
        assertEquals(FieldState.NOT_APPLICABLE, state(sections, "EVIDENCE_SUMMARY", "aiAssisted"));
        assertEquals(FieldState.NOT_APPLICABLE, state(sections, "HUMAN_CONTRIBUTION", "humanCreated"));
        assertTrue(sections.get(1).written());
        assertTrue(sections.get(2).written());
    }
}
