package com.line4.idea2ip.integration.ai;

import com.line4.idea2ip.domain.interview.entity.InterviewField;
import com.line4.idea2ip.domain.interview.entity.InterviewFieldState;
import com.line4.idea2ip.domain.material.entity.MaterialType;
import com.line4.idea2ip.integration.ai.AiInterviewClient.Answer;
import com.line4.idea2ip.integration.ai.AiInterviewClient.Context;
import com.line4.idea2ip.integration.ai.AiInterviewClient.Evidence;
import com.line4.idea2ip.integration.ai.AiInterviewClient.QuestionDraft;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Interview questions built by code from field templates, in both interview modes. No model writes a question:
 * a model-written question needed a second model call to review it, and that loop was where questions failed
 * (503), repeated, drifted to another field or leaked instruction text. The model now only reads answers.
 *
 * <p>A question links to the conversation through the user's own words: an earlier statement about this work
 * ({@link InterviewQuestions#related}), the tool name already given, or the part of the latest answer that needs
 * a closer look. Every candidate goes through {@link InterviewQuestions#unused}, so no question is asked twice.
 */
public final class InterviewQuestionTemplates {
    /** Reason of a question that re-checks work named in the latest answer; the answer after it is judged once. */
    public static final String CONFIRMATION_REASON = "이번 답변에서 말씀하신 작업을 확인합니다.";
    static final String DEFAULT_REASON = "창작 과정의 해당 항목을 확인합니다.";
    static final String FOLLOW_UP_REASON = "앞선 답변의 구체적인 내용을 확인합니다.";
    static final String CLARIFY_REASON = "앞선 답변에서 확인할 내용이 있습니다.";
    static final String TOOL_CONFIRMATION_REASON = "말씀하신 도구 이름이 어떤 AI 도구인지 확인합니다.";
    /** Clarification a live answer check returns when the reply could not be quoted; it is an instruction, not a question. */
    static final String CONFIRM_CURRENT_ANSWER = "사용자가 이번 답변에서 언급한 작업을 기준으로 해당 항목을 한 번만 확인하세요. "
            + "해당 작업이 있었는지 묻고, 있었다면 방법을 직접 설명할 수 있게 질문하세요.";
    /** A clarification phrased as an embedded question ("다른 AI 도구를 사용했는지") can be asked as is. */
    /** A clarification already written as a short question to the user ("어떤 서비스였나요?"). */
    private static final Pattern ASKABLE_QUESTION = Pattern.compile("^[^\\n‘’“”\"']{2,80}\\?$");
    /** Wording meant for the question writer, never shown to the user. */
    private static final Pattern INSTRUCTION = Pattern.compile("확인하세요|질문하세요|사용자|답변자|field|focus");
    private static final Pattern ASKABLE_CLARIFICATION = Pattern.compile("^[^\\n‘’“”\"']{2,80}(?:는지|은지|인지|ㄴ지|한지|했는지|할지|을지|일지)$");
    private static final Pattern TOOL_NAME = Pattern.compile("^[\\p{L}\\p{N} .+\\-]{1,30}$");

    private InterviewQuestionTemplates() {}

    /**
     * The next question for {@code field}. {@code clarification} is what the answer check found unclear in the
     * latest answer to this field; null when the field is asked for the first time or nothing specific is unclear.
     */
    public static QuestionDraft draft(Context context, InterviewField field, String clarification) {
        boolean followUp = context.answers().stream().anyMatch(answer -> answer.field() == field);
        var asked = InterviewQuestions.recentAndSameField(context, field);
        if (!followUp) return opening(context, field, asked);

        String latest = context.answers().reversed().stream().filter(answer -> answer.field() == field)
                .map(Answer::content).findFirst().orElse(null);
        // "‘소라’라고 하셨는데, OpenAI의 Sora를 말씀하신 게 맞나요?": written in full by the answer check.
        if (field == InterviewField.AI_TOOL && KnownAiTools.confirming(clarification).isPresent()) {
            return new QuestionDraft(clarification.strip(), TOOL_CONFIRMATION_REASON, List.of());
        }
        if (clarification != null && !clarification.isBlank()) {
            var clarifying = clarifying(field, clarification.strip(), latest, asked);
            if (clarifying.isPresent()) return clarifying.get();
        }
        var supplement = InterviewSupplement.next(field, InterviewSupplement.answersFor(context, field));
        String prompt = InterviewQuestions.followUp(field, asked, supplement, latest);
        if (InterviewQuestions.isClosingQuestion(field, prompt)) {
            return new QuestionDraft(prompt, InterviewQuestions.CLOSING_REASON, List.of());
        }
        if (InterviewQuestions.isNoAiConfirmation(prompt)) {
            return new QuestionDraft(prompt, InterviewQuestions.NO_AI_CONFIRMATION_REASON, List.of());
        }
        String reason = supplement.map(value -> value.reason).orElse(FOLLOW_UP_REASON);
        return withMaterial(context, field, prompt, reason);
    }

    private static QuestionDraft opening(Context context, InterviewField field, List<String> asked) {
        var related = InterviewQuestions.related(context, field);
        if (related.isPresent()) {
            return new QuestionDraft(InterviewQuestions.unused(field, InterviewQuestions.opening(field, related.get()), asked),
                    InterviewQuestions.OPENING_REASON, List.of());
        }
        String preferred = toolName(context).map(tool -> withTool(field, tool)).orElse(null);
        String prompt = InterviewQuestions.unused(field,
                preferred != null ? preferred : MockAiInterviewClient.baseQuestion(field), asked);
        return withMaterial(context, field, prompt, DEFAULT_REASON);
    }

    /** AI questions name the tool already given, so they follow on from the tool answer. */
    private static String withTool(InterviewField field, String tool) {
        return switch (field) {
            case AI_PURPOSE -> tool + " 도구를 어떤 작업에, 어떤 목적으로 사용했나요?";
            case AI_OUTPUT_USAGE -> tool + " 도구로 만든 결과를 최종 작품에 넣었나요? 넣었다면 어느 부분에 어떻게 썼는지 알려주세요.";
            case AI_TOOL, HUMAN_CREATED_ELEMENT, HUMAN_EDITED_EXPRESSION -> null;
        };
    }

    private static Optional<String> toolName(Context context) {
        return context.fields().stream()
                .filter(state -> state.field() == InterviewField.AI_TOOL
                        && state.status() == InterviewFieldState.Status.COMPLETED)
                .map(InterviewFieldState::summary)
                .filter(summary -> summary != null && !summary.isBlank())
                // A known tool is named the usual way ("제미나이로" → "Gemini"); other names only when they are bare.
                .map(summary -> KnownAiTools.displayName(summary).orElse(isToolName(summary.strip()) ? summary.strip() : null))
                .filter(name -> name != null)
                .findFirst();
    }

    /**
     * Whether text reads as a bare tool name ("Stable Diffusion", "스테이블 디퓨젼") rather than a sentence about
     * using one ("Stable Diffusion을 사용했습니다."), which may only be quoted, never slotted into a question.
     */
    static boolean isToolName(String text) {
        String value = text.strip();
        return TOOL_NAME.matcher(value).matches() && value.split("\\s+").length <= 4
                && !value.endsWith("다") && !value.endsWith("요") && !value.endsWith(".");
    }

    private static Optional<QuestionDraft> clarifying(InterviewField field, String clarification, String latest,
                                                      List<String> asked) {
        String quote = InterviewQuestions.quote(latest);
        // "ㅁㄹ... ㅅㅈ ㅇㄱ?" reads as nothing when quoted back; such a reply is not repeated to the user.
        String lead = readable(quote) ? "‘" + quote + "’라고 하셨는데, " : "";
        if (clarification.equals(CONFIRM_CURRENT_ANSWER)) {
            String question = lead + confirmation(field);
            return InterviewQuestions.alreadyAsked(question, asked) ? Optional.empty()
                    : Optional.of(new QuestionDraft(question, CONFIRMATION_REASON, List.of()));
        }
        String question;
        if (ASKABLE_CLARIFICATION.matcher(clarification).matches()) {
            question = lead + clarification + " 알려주세요.";
        } else if (ASKABLE_QUESTION.matcher(clarification).matches() && !INSTRUCTION.matcher(clarification).find()) {
            // "어떤 서비스였나요?": a question the check already wrote for the user follows on from their own words.
            question = lead + clarification;
        } else {
            return Optional.empty();
        }
        return InterviewQuestions.alreadyAsked(question, asked) ? Optional.empty()
                : Optional.of(new QuestionDraft(question, CLARIFY_REASON, List.of()));
    }

    /** At least three letters other than bare Hangul consonants and vowels (ㄱ-ㆎ). */
    static boolean readable(String text) {
        return text.codePoints().filter(Character::isLetter).filter(c -> c < 0x3131 || c > 0x318E).count() >= 3;
    }

    private static String confirmation(InterviewField field) {
        return switch (field) {
            case AI_TOOL -> "사용한 AI 도구의 이름을 알려주세요. 사용하지 않았다면 ‘없음’이라고 알려주세요.";
            case AI_PURPOSE -> "AI를 어떤 작업에, 어떤 목적으로 썼는지 조금 더 구체적으로 알려주세요.";
            case AI_OUTPUT_USAGE -> "AI 결과를 최종 작품에 넣었는지, 넣었다면 어느 부분인지 알려주세요.";
            case HUMAN_CREATED_ELEMENT -> "직접 만든 요소가 무엇이고 어떤 방식으로 만들었는지 알려주세요.";
            case HUMAN_EDITED_EXPRESSION -> "직접 바꾼 부분이 무엇이고 어떻게 바꿨는지 알려주세요.";
        };
    }

    /** Links one material of the type that usually documents the field, as the question's reference. */
    private static QuestionDraft withMaterial(Context context, InterviewField field, String prompt, String reason) {
        MaterialType type = MockAiInterviewClient.referenceType(field);
        List<String> sourceIds = context.evidence().stream().filter(source -> source.type() == type)
                .map(Evidence::sourceId).limit(1).toList();
        return new QuestionDraft(prompt, reason, sourceIds);
    }
}
