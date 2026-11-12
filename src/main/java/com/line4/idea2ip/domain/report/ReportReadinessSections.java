package com.line4.idea2ip.domain.report;

import com.line4.idea2ip.domain.contribution.ContributionModels.Confirmed;
import com.line4.idea2ip.domain.interview.entity.InterviewField;
import com.line4.idea2ip.domain.interview.entity.InterviewFieldState.Status;
import com.line4.idea2ip.domain.material.entity.MaterialType;
import com.line4.idea2ip.domain.report.ReportModels.FieldState;
import com.line4.idea2ip.domain.report.ReportModels.ReadinessField;
import com.line4.idea2ip.domain.report.ReportModels.ReadinessSection;
import com.line4.idea2ip.domain.report.ReportModels.ReadinessSummary;
import com.line4.idea2ip.domain.report.ReportModels.SectionKey;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Per-field "written / not applicable / unverified" state of the four report sections the spec shows before
 * generation. It describes what the report will contain, not whether legal requirements are met, and never
 * blocks generation: unverified fields end up in the report's gaps section.
 */
final class ReportReadinessSections {
    private ReportReadinessSections() {}

    record Inputs(String artworkType, String projectDescription, boolean artworkImage, String artworkDescription,
                  List<Confirmed> confirmed, String noContributionReason, Map<InterviewField, Status> interview,
                  Set<MaterialType> materialTypesWithFiles) {}

    static List<ReadinessSection> sections(Inputs in) {
        boolean noAi = in.interview().get(InterviewField.AI_TOOL) == Status.NOT_APPLICABLE;
        boolean noHumanWork = in.noContributionReason() != null && in.confirmed().stream().allMatch(step ->
                step.content().humanCreatedElements().isEmpty() && step.content().humanEdits().isEmpty());

        var overview = List.of(
                field("artworkType", "작품 유형", written(in.artworkType())),
                field("projectDescription", "주제·기획 의도", written(in.projectDescription())),
                field("artworkImage", "최종 작품 이미지", in.artworkImage() ? FieldState.WRITTEN : FieldState.UNVERIFIED),
                field("artworkDescription", "최종 작품 설명", written(in.artworkDescription())));
        var ai = List.of(
                aiField(in, "tool", "사용한 AI 도구", InterviewField.AI_TOOL, step -> step.content().aiUsage() != null
                        && step.content().aiUsage().tools() != null && !step.content().aiUsage().tools().isEmpty(), noAi),
                aiField(in, "purpose", "AI 활용 목적", InterviewField.AI_PURPOSE, step -> step.content().aiUsage() != null
                        && hasText(step.content().aiUsage().purpose()), noAi),
                aiField(in, "outputUsage", "AI 결과 활용", InterviewField.AI_OUTPUT_USAGE, step -> step.content().aiUsage() != null
                        && hasText(step.content().aiUsage().outputUsage()), noAi));
        var human = List.of(
                humanField(in, "humanCreated", "직접 창작한 부분", InterviewField.HUMAN_CREATED_ELEMENT,
                        step -> !step.content().humanCreatedElements().isEmpty(), noHumanWork),
                humanField(in, "humanEdited", "직접 수정한 부분", InterviewField.HUMAN_EDITED_EXPRESSION,
                        step -> !step.content().humanEdits().isEmpty(), noHumanWork));
        var references = List.of(
                materialField(in, "initialSketch", "초기 스케치", MaterialType.INITIAL_SKETCH, false),
                materialField(in, "aiAssisted", "AI 산출물 자료", MaterialType.AI_ASSISTED, noAi),
                materialField(in, "manualEdit", "직접 수정 자료", MaterialType.MANUAL_EDIT, false));
        return List.of(section(SectionKey.WORK_OVERVIEW, overview), section(SectionKey.AI_USAGE, ai),
                section(SectionKey.HUMAN_CONTRIBUTION, human), section(SectionKey.EVIDENCE_SUMMARY, references));
    }

    static ReadinessSummary summary(List<ReadinessSection> sections) {
        var fields = sections.stream().flatMap(section -> section.fields().stream()).toList();
        return new ReadinessSummary(count(fields, FieldState.WRITTEN), count(fields, FieldState.NOT_APPLICABLE),
                count(fields, FieldState.UNVERIFIED), fields.size());
    }

    private static ReadinessField aiField(Inputs in, String key, String label, InterviewField interviewField,
                                          Predicate<Confirmed> answered, boolean noAi) {
        if (in.confirmed().stream().anyMatch(answered)) return field(key, label, FieldState.WRITTEN);
        boolean notApplicable = noAi || in.interview().get(interviewField) == Status.NOT_APPLICABLE;
        return field(key, label, notApplicable ? FieldState.NOT_APPLICABLE : FieldState.UNVERIFIED);
    }

    private static ReadinessField humanField(Inputs in, String key, String label, InterviewField interviewField,
                                             Predicate<Confirmed> answered, boolean noHumanWork) {
        if (in.confirmed().stream().anyMatch(answered)) return field(key, label, FieldState.WRITTEN);
        boolean notApplicable = noHumanWork || in.interview().get(interviewField) == Status.NOT_APPLICABLE;
        return field(key, label, notApplicable ? FieldState.NOT_APPLICABLE : FieldState.UNVERIFIED);
    }

    private static ReadinessField materialField(Inputs in, String key, String label, MaterialType type, boolean notApplicable) {
        if (in.materialTypesWithFiles().contains(type)) return field(key, label, FieldState.WRITTEN);
        return field(key, label, notApplicable ? FieldState.NOT_APPLICABLE : FieldState.UNVERIFIED);
    }

    private static ReadinessSection section(SectionKey key, List<ReadinessField> fields) {
        int written = count(fields, FieldState.WRITTEN);
        boolean complete = fields.stream().noneMatch(field -> field.state() == FieldState.UNVERIFIED);
        return new ReadinessSection(key.name(), key.title(), complete, written, fields.size(), fields);
    }

    private static ReadinessField field(String key, String label, FieldState state) {
        return new ReadinessField(key, label, state);
    }

    private static FieldState written(String value) {
        return hasText(value) ? FieldState.WRITTEN : FieldState.UNVERIFIED;
    }

    private static int count(List<ReadinessField> fields, FieldState state) {
        return (int) fields.stream().filter(field -> field.state() == state).count();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
