package com.line4.idea2ip.integration.ai;

import com.line4.idea2ip.domain.interview.entity.InterviewField;
import com.line4.idea2ip.domain.interview.entity.InterviewFieldState;

import java.util.List;
import java.util.Optional;

/**
 * One supplementary question inside an existing interview field, asked when the first answer describes the work
 * but leaves out what the report needs: the concrete creative choice (what was selected or placed, where and why)
 * for human work, and the production stage for AI output. Answers keep the field's targetField, so the interview
 * API, field states and contribution structure stay unchanged; completed interviews are never reopened.
 */
enum InterviewSupplement {
    CREATIVE_CHOICE(
            "앞선 답변의 구체적인 창작적 선택을 확인합니다.",
            "앞선 답변의 작업에서 어떤 요소를 직접 선택하거나 배치했는지, 최종 작품의 어디에 두었는지, 그렇게 정한 이유",
            "그 작업에서 어떤 요소를 직접 선택하거나 배치했나요? 최종 작품의 어디에 두었고, 그렇게 정한 이유도 알려주세요. "
                    + "따로 선택·배치한 것이 없다면 없다고 알려주세요.",
            List.of("배치", "위치", "선택", "골라", "골랐", "구성", "이유", "때문", "의도", "위해", "하려고", "보이도록", "왼쪽", "오른쪽", "위쪽", "아래", "중앙", "가운데")),
    PRODUCTION_STAGE(
            "AI 결과를 사용한 제작 단계를 확인합니다.",
            "앞선 답변의 AI 결과를 아이디어 구상, 구도 결정, 최종 작품 제작 중 어느 단계에서 사용했는지",
            "그 AI 결과는 아이디어 구상, 구도 결정, 최종 작품 제작 중 어느 단계에서 사용했나요?",
            List.of("단계", "아이디어", "구상", "구도", "초안", "스케치", "시안", "최종", "마무리", "채색", "배경", "참고만"));

    /** Shortest first answer that counts as describing the work; shorter ones get the usual follow-up instead. */
    private static final int SUBSTANTIVE_LENGTH = 8;

    final String reason;
    final String focus;
    final String question;
    private final List<String> covered;

    InterviewSupplement(String reason, String focus, String question, List<String> covered) {
        this.reason = reason;
        this.focus = focus;
        this.question = question;
        this.covered = covered;
    }

    /**
     * The supplement to ask next, given every answer to the field so far (newest last). Only after a single
     * substantive answer that does not already cover the topic, so each field gets at most one supplement.
     */
    static Optional<InterviewSupplement> next(InterviewField field, List<String> answersForField) {
        if (answersForField.size() != 1) return Optional.empty();
        String answer = answersForField.getFirst();
        if (InterviewFieldState.isNotApplicableAnswer(answer)
                || answer.strip().codePointCount(0, answer.strip().length()) < SUBSTANTIVE_LENGTH) {
            return Optional.empty();
        }
        var supplement = switch (field) {
            case HUMAN_CREATED_ELEMENT, HUMAN_EDITED_EXPRESSION -> CREATIVE_CHOICE;
            case AI_OUTPUT_USAGE -> PRODUCTION_STAGE;
            case AI_TOOL, AI_PURPOSE -> null;
        };
        if (supplement == null || supplement.covered.stream().anyMatch(answer::contains)) return Optional.empty();
        return Optional.of(supplement);
    }

    static List<String> answersFor(AiInterviewClient.Context context, InterviewField field) {
        return context.answers().stream().filter(answer -> answer.field() == field)
                .map(AiInterviewClient.Answer::content).toList();
    }

    /** Answers to the field ending with {@code current}, whether or not the context already holds it. */
    static List<String> answersWith(AiInterviewClient.Context context, AiInterviewClient.Answer current) {
        var answers = new java.util.ArrayList<>(context.answers().stream()
                .filter(answer -> answer.field() == current.field() && !java.util.Objects.equals(answer.messageId(), current.messageId()))
                .map(AiInterviewClient.Answer::content).toList());
        answers.add(current.content());
        return List.copyOf(answers);
    }
}
