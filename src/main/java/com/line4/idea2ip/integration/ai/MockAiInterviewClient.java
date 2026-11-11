package com.line4.idea2ip.integration.ai;

import com.line4.idea2ip.domain.interview.entity.InterviewField;
import com.line4.idea2ip.domain.interview.entity.InterviewFieldState;
import com.line4.idea2ip.domain.material.entity.MaterialType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

import static com.line4.idea2ip.domain.interview.entity.InterviewFieldState.Status.*;

@Component
class MockAiInterviewClient implements AiInterviewClient {
    private static final Set<String> NOT_APPLICABLE_ANSWERS = Set.of("해당 없음", "없음", "없어요", "없습니다");

    @Override
    public QuestionDraft generateQuestion(Context context, InterviewField field) {
        String prompt = switch (field) {
            case AI_TOOL -> "창작 과정에서 어떤 AI 도구를 사용했나요?";
            case AI_PURPOSE -> "그 AI 도구를 어떤 작업에, 어떤 목적으로 사용했나요?";
            case AI_OUTPUT_USAGE -> "AI가 만든 결과 중 어떤 부분을 작품에 활용했나요?";
            case HUMAN_CREATED_ELEMENT -> "직접 창작한 요소가 있다면 무엇을 어떻게 만들었나요?";
            case HUMAN_EDITED_EXPRESSION -> "직접 수정하거나 추가한 표현이 있다면 어떻게 작업했나요?";
        };
        boolean followUp = context.answers().stream().anyMatch(answer -> answer.field() == field);
        if (followUp) {
            prompt = "앞선 답변을 조금 더 구체적으로 알려주세요. " + prompt;
        }
        MaterialType referenceType = switch (field) {
            case AI_TOOL, AI_PURPOSE, AI_OUTPUT_USAGE -> MaterialType.AI_ASSISTED;
            case HUMAN_CREATED_ELEMENT -> MaterialType.INITIAL_SKETCH;
            case HUMAN_EDITED_EXPRESSION -> MaterialType.MANUAL_EDIT;
        };
        List<String> sourceIds = context.evidence().stream()
                .filter(source -> source.type() == referenceType)
                .map(Evidence::sourceId)
                .limit(1)
                .toList();
        String reason = followUp ? "앞선 답변의 구체적인 내용을 확인합니다." : "창작 과정의 해당 항목을 확인합니다.";
        return new QuestionDraft(prompt, reason, sourceIds);
    }

    @Override
    public InterviewFieldState analyzeAnswer(Context context, Answer answer) {
        String text = answer.content();
        String phrase = text.endsWith(".") ? text.substring(0, text.length() - 1).stripTrailing() : text;
        if (NOT_APPLICABLE_ANSWERS.contains(phrase)) {
            boolean allowed = answer.field() == InterviewField.HUMAN_CREATED_ELEMENT
                    || answer.field() == InterviewField.HUMAN_EDITED_EXPRESSION;
            return new InterviewFieldState(answer.field(), allowed ? NOT_APPLICABLE : NEEDS_REVIEW, text);
        }

        // Keep the mock deterministic. Its length check does not evaluate the substance of a statement.
        int minimumLength = answer.field() == InterviewField.AI_TOOL ? 2 : 8;
        var status = text.codePointCount(0, text.length()) >= minimumLength ? COMPLETED : NEEDS_REVIEW;
        return new InterviewFieldState(answer.field(), status, text);
    }
}
