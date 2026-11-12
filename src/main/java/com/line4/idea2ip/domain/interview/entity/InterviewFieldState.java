package com.line4.idea2ip.domain.interview.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * @param closed true when the interview stopped asking about this field after the answer limit; the field keeps
 *               its NEEDS_REVIEW status so the gap stays visible, but it no longer holds the interview open.
 *               Absent in states stored before this flag existed, which read as false (a wrapper type because
 *               Jackson rejects a missing primitive by default).
 */
public record InterviewFieldState(InterviewField field, Status status, String summary, Boolean closed) {
    @JsonCreator
    public InterviewFieldState {
        closed = Boolean.TRUE.equals(closed);
    }

    public InterviewFieldState(InterviewField field, Status status, String summary) {
        this(field, status, summary, false);
    }

    private static final Set<String> NOT_APPLICABLE_ANSWERS = Set.of("해당 없음", "없음", "없어요", "없습니다");

    /** Whether an answer only states that the asked-about work does not exist. */
    public static boolean isNotApplicableAnswer(String answer) {
        if (answer == null) return false;
        String phrase = answer.strip();
        if (phrase.endsWith(".")) phrase = phrase.substring(0, phrase.length() - 1).strip();
        return NOT_APPLICABLE_ANSWERS.contains(phrase);
    }

    public enum Status {
        MISSING,
        NEEDS_REVIEW,
        COMPLETED,
        NOT_APPLICABLE
    }

    public static List<InterviewFieldState> initialStates() {
        return Arrays.stream(InterviewField.values())
                .map(field -> new InterviewFieldState(field, Status.MISSING, null))
                .toList();
    }

    /** Whether the interview should still ask about this field. */
    public boolean needsAnswer() {
        return !closed && (status == Status.MISSING || status == Status.NEEDS_REVIEW);
    }
}
