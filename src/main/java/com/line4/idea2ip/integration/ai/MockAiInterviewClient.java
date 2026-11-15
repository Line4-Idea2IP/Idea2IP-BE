package com.line4.idea2ip.integration.ai;

import com.line4.idea2ip.domain.interview.entity.InterviewField;
import com.line4.idea2ip.domain.interview.entity.InterviewFieldState;
import com.line4.idea2ip.domain.interview.entity.NoAiAnswer;
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
        boolean followUp = context.answers().stream().anyMatch(answer -> answer.field() == field);
        var supplement = InterviewSupplement.next(field, InterviewSupplement.answersFor(context, field));
        // Same rules as the live client: a repeated follow-up never reuses an earlier question of this field.
        var recent = InterviewQuestions.recentAndSameField(context, field);
        String latest = context.answers().reversed().stream().filter(answer -> answer.field() == field)
                .map(Answer::content).findFirst().orElse(null);
        var related = followUp ? java.util.Optional.<InterviewQuestions.Related>empty()
                : InterviewQuestions.related(context, field);
        if (related.isPresent()) {
            return new QuestionDraft(InterviewQuestions.unused(field, InterviewQuestions.opening(field, related.get()), recent),
                    InterviewQuestions.OPENING_REASON, List.of());
        }
        String prompt = followUp ? InterviewQuestions.followUp(field, recent, supplement, latest)
                : InterviewQuestions.unused(field, baseQuestion(field), recent);
        if (InterviewQuestions.isClosingQuestion(field, prompt)) {
            return new QuestionDraft(prompt, InterviewQuestions.CLOSING_REASON, List.of());
        }
        if (InterviewQuestions.isNoAiConfirmation(prompt)) {
            return new QuestionDraft(prompt, InterviewQuestions.NO_AI_CONFIRMATION_REASON, List.of());
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
        boolean none = InterviewFieldState.isNotApplicableAnswer(text);
        boolean closing = InterviewQuestions.isClosingQuestion(answer.field(), answer.question());
        if (answer.field() == InterviewField.AI_TOOL) {
            var aiTool = aiTool(answer, closing, answers);
            if (aiTool != null) return aiTool;
        }
        if (none && closing) {
            return new InterviewFieldState(answer.field(), NEEDS_REVIEW, String.join(" / ", answers), true);
        }
        var earlier = answers.subList(0, answers.size() - 1);
        if (InterviewSupplement.next(answer.field(), earlier).isPresent()) {
            // This replies to the supplementary question; any reply, including "없습니다", completes the field.
            return new InterviewFieldState(answer.field(), COMPLETED, earlier.getFirst() + " / " + text);
        }
        if (none) {
            String opening = InterviewQuestions.isOpeningQuestion(answer.field(), answer.question())
                    ? answer.question()
                    : InterviewQuestions.askedFor(context, answer.field()).stream()
                            .filter(question -> InterviewQuestions.isOpeningQuestion(answer.field(), question))
                            .findFirst().orElse(null);
            if (opening != null) {
                // A bare "없음" after an opening about already reported work leaves a gap in the details.
                return new InterviewFieldState(answer.field(), NEEDS_REVIEW,
                        "앞선 답변: " + InterviewQuestions.openingStatement(opening) + " / " + String.join(" / ", answers));
            }
            // AI materials are optional, so "없음" is a valid answer for AI fields as well as human work.
            return new InterviewFieldState(answer.field(), NOT_APPLICABLE, text);
        }

        // Keep the mock deterministic. Its length check does not evaluate the substance of a statement.
        int minimumLength = answer.field() == InterviewField.AI_TOOL ? 2 : 8;
        var status = text.codePointCount(0, text.length()) >= minimumLength ? COMPLETED : NEEDS_REVIEW;
        // Keep the field open for one supplementary question when the answer leaves out what the report needs.
        if (status == COMPLETED && InterviewSupplement.next(answer.field(), answers).isPresent()) status = NEEDS_REVIEW;
        return new InterviewFieldState(answer.field(), status, text);
    }

    /** "No AI" answers to the AI tool question, or null to apply the general rules (a tool name, say). */
    private static InterviewFieldState aiTool(Answer answer, boolean closing, List<String> answers) {
        String text = answer.content();
        if (InterviewQuestions.isNoAiConfirmation(answer.question())) {
            if (NoAiAnswer.confirms(text)) {
                return new InterviewFieldState(InterviewField.AI_TOOL, NOT_APPLICABLE, InterviewQuestions.NO_AI_CONFIRMED);
            }
            // "아니요": AI was used after all, so ask for the tool's name again.
            if (NoAiAnswer.denies(text)) return new InterviewFieldState(InterviewField.AI_TOOL, NEEDS_REVIEW, text);
            // "챗GPT는 안 썼어요": neither a yes nor a tool name, so the tool name is asked for again.
            if (NoAiAnswer.notAToolName(text)) return new InterviewFieldState(InterviewField.AI_TOOL, NEEDS_REVIEW, text);
            return null;
        }
        boolean noneLike = InterviewFieldState.isNotApplicableAnswer(text) || NoAiAnswer.clearlyNone(text)
                || NoAiAnswer.unclearNone(text);
        // A closing question asks whether there is more to add, so "없음" there means nothing more.
        if (closing && noneLike) {
            return new InterviewFieldState(InterviewField.AI_TOOL, NEEDS_REVIEW, String.join(" / ", answers), true);
        }
        if (NoAiAnswer.clearlyNone(text)) return new InterviewFieldState(InterviewField.AI_TOOL, NOT_APPLICABLE, text);
        // Unclear "none" asks for confirmation next; a bare "네" names no tool, so the tool question is asked again.
        if (NoAiAnswer.unclearNone(text) || !InterviewFieldState.isNotApplicableAnswer(text) && NoAiAnswer.notAToolName(text)) {
            return new InterviewFieldState(InterviewField.AI_TOOL, NEEDS_REVIEW, text);
        }
        return null;
    }
}
