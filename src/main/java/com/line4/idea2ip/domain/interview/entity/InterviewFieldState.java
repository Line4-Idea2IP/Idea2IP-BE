package com.line4.idea2ip.domain.interview.entity;

import java.util.Arrays;
import java.util.List;

public record InterviewFieldState(InterviewField field, Status status, String summary) {
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
