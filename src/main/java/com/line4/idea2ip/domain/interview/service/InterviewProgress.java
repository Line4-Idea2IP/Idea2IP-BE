package com.line4.idea2ip.domain.interview.service;

import com.line4.idea2ip.domain.interview.entity.InterviewField;
import com.line4.idea2ip.domain.interview.entity.InterviewFieldState;
import com.line4.idea2ip.domain.interview.entity.InterviewFieldState.Status;

import java.util.List;
import java.util.Set;

/**
 * Interview rules that hold whatever the answer evaluator decides, so an interview always reaches its end:
 * a field is asked at most {@link #MAX_ANSWERS_PER_FIELD} times, and answering that no AI tool was used
 * settles the other AI fields instead of asking about AI work that did not happen.
 */
final class InterviewProgress {
    static final int MAX_ANSWERS_PER_FIELD = 3;
    static final String NO_AI_SUMMARY = "AI 도구를 사용하지 않았다고 답변했습니다.";
    private static final Set<InterviewField> AI_DETAILS = Set.of(InterviewField.AI_PURPOSE, InterviewField.AI_OUTPUT_USAGE);

    private InterviewProgress() {}

    /**
     * Applies an evaluation to the field states.
     *
     * @param answersForField every answer to the evaluated field so far, including the one just evaluated
     */
    static List<InterviewFieldState> apply(List<InterviewFieldState> fields, InterviewFieldState evaluation,
                                           List<String> answersForField) {
        var settled = evaluation;
        if (evaluation.needsAnswer() && answersForField.size() >= MAX_ANSWERS_PER_FIELD) {
            // Stop asking: keep what the user said and let the contribution review and report show it as given.
            settled = new InterviewFieldState(evaluation.field(), Status.COMPLETED, String.join(" / ", answersForField));
        }
        var result = settled;
        boolean noAi = result.field() == InterviewField.AI_TOOL && result.status() == Status.NOT_APPLICABLE;
        return fields.stream().map(field -> {
            if (field.field() == result.field()) return result;
            if (noAi && AI_DETAILS.contains(field.field()) && field.needsAnswer()) {
                return new InterviewFieldState(field.field(), Status.NOT_APPLICABLE, NO_AI_SUMMARY);
            }
            return field;
        }).toList();
    }
}
