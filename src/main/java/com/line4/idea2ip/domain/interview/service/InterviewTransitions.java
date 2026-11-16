package com.line4.idea2ip.domain.interview.service;

import com.line4.idea2ip.domain.interview.entity.InterviewField;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The short lead-in that joins a question to the conversation: which fields the user's own words already settled
 * (so they know why those are not asked), a move from AI use to their own work, or a second look at the same
 * field. Questions are stored with the lead-in, but everything that compares or interprets a question uses
 * {@link #body}, so a lead-in never hides a repeated question or changes how an answer is read.
 */
final class InterviewTransitions {
    static final int QUESTION_MAX = 500;
    private static final Set<InterviewField> AI_FIELDS =
            EnumSet.of(InterviewField.AI_TOOL, InterviewField.AI_PURPOSE, InterviewField.AI_OUTPUT_USAGE);
    private static final String FROM_ANSWERS = "말씀하신 내용으로 ";
    private static final String FROM_DESCRIPTION = "작품 설명에서 ";
    private static final String SETTLED_TAIL = "까지 확인했습니다. ";
    private static final String TO_OWN_WORK = "이제 직접 작업하신 부분을 여쭤볼게요. ";
    private static final String AGAIN = "조금만 더 여쭤볼게요. ";
    private static final Pattern LEAD_IN = Pattern.compile(
            "^(?:(?:말씀하신 내용으로|작품 설명에서) [^.?!]{1,160}까지 확인했습니다\\. )?"
                    + "(?:이제 직접 작업하신 부분을 여쭤볼게요\\. |조금만 더 여쭤볼게요\\. )?");

    private InterviewTransitions() {}

    /**
     * @param previous the field of the question just answered, or null for the first question
     * @param settled  fields completed from the user's words without being asked, in field order
     * @param fromDescription whether {@code settled} came from the project descriptions rather than an answer
     */
    static String leadIn(InterviewField previous, InterviewField next, List<InterviewField> settled, boolean fromDescription) {
        var text = new StringBuilder();
        if (!settled.isEmpty()) {
            text.append(fromDescription ? FROM_DESCRIPTION : FROM_ANSWERS)
                    .append(settled.stream().map(InterviewTransitions::label).collect(Collectors.joining(", ")))
                    .append(SETTLED_TAIL);
        }
        if (previous != null && previous == next) {
            text.append(AGAIN);
        } else if ((previous == null ? !settled.isEmpty() && settled.stream().allMatch(AI_FIELDS::contains)
                : AI_FIELDS.contains(previous)) && !AI_FIELDS.contains(next)) {
            text.append(TO_OWN_WORK);
        }
        return text.toString();
    }

    /** The question with its lead-in, or the bare question when both would not fit the stored length. */
    static String withLeadIn(String leadIn, String question) {
        String joined = leadIn + question;
        return joined.length() <= QUESTION_MAX ? joined : question;
    }

    /** A stored question without its lead-in. */
    static String body(String question) {
        if (question == null) return null;
        var matcher = LEAD_IN.matcher(question);
        return matcher.lookingAt() && matcher.end() < question.length() ? question.substring(matcher.end()) : question;
    }

    static String label(InterviewField field) {
        return switch (field) {
            case AI_TOOL -> "사용한 AI 도구";
            case AI_PURPOSE -> "AI 사용 목적";
            case AI_OUTPUT_USAGE -> "최종 작품 활용 여부";
            case HUMAN_CREATED_ELEMENT -> "직접 만든 요소";
            case HUMAN_EDITED_EXPRESSION -> "직접 수정한 표현";
        };
    }
}
