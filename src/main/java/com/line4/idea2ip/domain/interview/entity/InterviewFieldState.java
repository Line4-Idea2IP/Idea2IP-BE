package com.line4.idea2ip.domain.interview.entity;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public record InterviewFieldState(InterviewField field, Status status, String summary) {
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

    public boolean needsAnswer() {
        return status == Status.MISSING || status == Status.NEEDS_REVIEW;
    }
}
