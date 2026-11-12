package com.line4.idea2ip.integration.ai;

import com.line4.idea2ip.domain.interview.entity.InterviewField;
import com.line4.idea2ip.domain.interview.entity.InterviewFieldState;
import com.line4.idea2ip.domain.material.entity.MaterialType;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.line4.idea2ip.domain.interview.entity.InterviewFieldState.Status.*;

@Component
class MockAiInterviewClient implements AiInterviewClient {
    /** Neutral default question for a field; also the example given to the live model. */
    static String baseQuestion(InterviewField field) {
        return switch (field) {
            case AI_TOOL -> "창작 과정에서 어떤 AI 도구를 사용했나요?";
            case AI_PURPOSE -> "그 AI 도구를 어떤 작업에, 어떤 목적으로 사용했나요?";
            case AI_OUTPUT_USAGE -> "AI가 만든 결과를 최종 작품에 활용했나요? 활용했다면 어떤 부분을 어떻게 활용했는지 알려주세요.";
            case HUMAN_CREATED_ELEMENT -> "직접 새로 만든 요소가 있다면 무엇이고, 어떤 방식으로 만들었나요? 없다면 없다고 알려주세요.";
            case HUMAN_EDITED_EXPRESSION -> "직접 수정하거나 추가한 표현이 있다면 무엇을 어떻게 작업했나요? 없다면 없다고 알려주세요.";
        };
    }

    /** Material type that usually documents the work a field asks about. */
    static MaterialType referenceType(InterviewField field) {
        return switch (field) {
            case AI_TOOL, AI_PURPOSE, AI_OUTPUT_USAGE -> MaterialType.AI_ASSISTED;
            case HUMAN_CREATED_ELEMENT -> MaterialType.INITIAL_SKETCH;
            case HUMAN_EDITED_EXPRESSION -> MaterialType.MANUAL_EDIT;
        };
    }

    @Override
    public QuestionDraft generateQuestion(Context context, InterviewField field) {
        String prompt = baseQuestion(field);
        boolean followUp = context.answers().stream().anyMatch(answer -> answer.field() == field);
        var supplement = InterviewSupplement.next(field, InterviewSupplement.answersFor(context, field));
        if (supplement.isPresent()) {
            prompt = supplement.get().question;
        } else if (followUp) {
            prompt = "앞선 답변을 조금 더 구체적으로 알려주세요. " + prompt;
        }
        MaterialType referenceType = referenceType(field);
        List<String> sourceIds = context.evidence().stream()
                .filter(source -> source.type() == referenceType)
                .map(Evidence::sourceId)
                .limit(1)
                .toList();
        String reason = supplement.map(value -> value.reason)
                .orElse(followUp ? "앞선 답변의 구체적인 내용을 확인합니다." : "창작 과정의 해당 항목을 확인합니다.");
        return new QuestionDraft(prompt, reason, sourceIds);
    }

    @Override
    public InterviewFieldState analyzeAnswer(Context context, Answer answer) {
        String text = answer.content();
        var answers = InterviewSupplement.answersWith(context, answer);
        var earlier = answers.subList(0, answers.size() - 1);
        if (InterviewSupplement.next(answer.field(), earlier).isPresent()) {
            // This replies to the supplementary question; any reply, including "없습니다", completes the field.
            return new InterviewFieldState(answer.field(), COMPLETED, earlier.getFirst() + " / " + text);
        }
        if (InterviewFieldState.isNotApplicableAnswer(text)) {
            boolean allowed = answer.field() == InterviewField.HUMAN_CREATED_ELEMENT
                    || answer.field() == InterviewField.HUMAN_EDITED_EXPRESSION;
            return new InterviewFieldState(answer.field(), allowed ? NOT_APPLICABLE : NEEDS_REVIEW, text);
        }

        // Keep the mock deterministic. Its length check does not evaluate the substance of a statement.
        int minimumLength = answer.field() == InterviewField.AI_TOOL ? 2 : 8;
        var status = text.codePointCount(0, text.length()) >= minimumLength ? COMPLETED : NEEDS_REVIEW;
        // Keep the field open for one supplementary question when the answer leaves out what the report needs.
        if (status == COMPLETED && InterviewSupplement.next(answer.field(), answers).isPresent()) status = NEEDS_REVIEW;
        return new InterviewFieldState(answer.field(), status, text);
    }
}
