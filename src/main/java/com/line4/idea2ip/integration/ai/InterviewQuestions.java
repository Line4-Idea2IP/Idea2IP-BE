package com.line4.idea2ip.integration.ai;

import com.line4.idea2ip.domain.interview.entity.InterviewField;
import com.line4.idea2ip.domain.interview.entity.InterviewFieldState;
import com.line4.idea2ip.integration.ai.AiInterviewClient.AskedQuestion;
import com.line4.idea2ip.integration.ai.AiInterviewClient.Context;
import com.line4.idea2ip.integration.ai.AiInterviewClient.PreviousTurn;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Question rules shared by the mock and live interview clients so neither repeats a question: field-specific
 * follow-ups for vague answers, duplicate detection across fields, and which earlier turns a new field may build on.
 */
final class InterviewQuestions {
    /** Duplicate checks look at this many of the most recent questions from any field. */
    static final int RECENT_QUESTIONS = 3;
    /** At most this many earlier turns from related fields are passed when a new field starts. */
    static final int EARLIER_TURNS = 6;
    private static final Pattern NON_WORD_CHARACTERS = Pattern.compile("[^\\p{L}\\p{M}\\p{N}]");
    private static final Set<InterviewField> AI_FIELDS =
            Set.of(InterviewField.AI_TOOL, InterviewField.AI_PURPOSE, InterviewField.AI_OUTPUT_USAGE);
    // Human work is often first mentioned while describing how AI output was used.
    private static final Set<InterviewField> HUMAN_CONTEXT = Set.of(InterviewField.AI_PURPOSE, InterviewField.AI_OUTPUT_USAGE,
            InterviewField.HUMAN_CREATED_ELEMENT, InterviewField.HUMAN_EDITED_EXPRESSION);

    private InterviewQuestions() {}

    /** Questions already asked for this field, oldest first. */
    static List<String> askedFor(Context context, InterviewField field) {
        return context.questions().stream().filter(question -> question.field() == field)
                .map(AskedQuestion::content).toList();
    }

    /** The last few questions from any field plus every question asked for this field. */
    static List<String> recentAndSameField(Context context, InterviewField field) {
        var questions = context.questions();
        var result = new ArrayList<>(questions.subList(Math.max(0, questions.size() - RECENT_QUESTIONS), questions.size())
                .stream().map(AskedQuestion::content).toList());
        result.addAll(askedFor(context, field));
        return List.copyOf(result);
    }

    /**
     * Next question for a field that was already asked: the supplement if it is still new, otherwise a narrower
     * field-specific question not asked yet. Never returns a question already in {@code asked}.
     */
    static String followUp(InterviewField field, List<String> asked, Optional<InterviewSupplement> supplement) {
        if (supplement.isPresent() && !alreadyAsked(supplement.get().question, asked)) {
            return supplement.get().question;
        }
        return unused(field, alternatives(field).getFirst(), asked);
    }

    /**
     * {@code preferred} if it was not asked yet, otherwise the first unused field question (narrower alternatives,
     * then the base question). Used whenever a model question has to be replaced, so the replacement is checked too.
     * The closing prompts ask about additional information and are distinct from the ordinary field questions.
     */
    static String unused(InterviewField field, String preferred, List<String> asked) {
        var candidates = new ArrayList<String>();
        candidates.add(preferred);
        candidates.addAll(alternatives(field));
        candidates.add(MockAiInterviewClient.baseQuestion(field));
        candidates.addAll(closingQuestions(field));
        var fresh = candidates.stream().filter(question -> !alreadyAsked(question, asked)).findFirst();
        if (fresh.isPresent()) return fresh.get();
        // Practically unreachable, but throwing here would fail every retry of the same answer and stall the
        // interview. Number the first closing question until it is new; asked is finite, so this ends.
        String closing = closingQuestions(field).getFirst();
        for (int round = 2; ; round++) {
            String question = "(" + round + "번째 확인) " + closing;
            if (!alreadyAsked(question, asked)) return question;
        }
    }

    static final String CLOSING_REASON = "앞선 답변에 덧붙일 정보가 있는지 확인합니다.";

    /** Whether a question is one of the field's closing prompts, including numbered fallbacks. */
    static boolean isClosingQuestion(InterviewField field, String question) {
        if (question == null) return false;
        String unnumbered = question.replaceFirst("^\\(\\d+번째 확인\\)\\s*", "");
        return alreadyAsked(unnumbered, closingQuestions(field));
    }

    private static List<String> closingQuestions(InterviewField field) {
        String name = "'" + label(field) + "'";
        return List.of(
                name + "에 대해 지금 확인해서 알려줄 내용이 있나요? 없다면 ‘없음’이라고 알려주세요.",
                name + "에 대해 앞선 답변에서 바로잡을 내용이 있나요? 없다면 ‘없음’이라고 알려주세요.",
                name + "과 관련해 나중에 확인할 수 있는 자료나 단서가 있나요? 없다면 ‘없음’이라고 알려주세요.");
    }

    private static String label(InterviewField field) {
        return switch (field) {
            case AI_TOOL -> "사용한 AI 도구";
            case AI_PURPOSE -> "AI 활용 목적";
            case AI_OUTPUT_USAGE -> "AI 결과 활용";
            case HUMAN_CREATED_ELEMENT -> "직접 창작한 부분";
            case HUMAN_EDITED_EXPRESSION -> "직접 수정한 부분";
        };
    }

    private static List<String> alternatives(InterviewField field) {
        return switch (field) {
            case AI_TOOL -> List.of("사용한 AI 도구 이름을 하나만 적어 주실 수 있나요? 사용하지 않았다면 ‘없음’이라고 알려주세요.",
                    "작업할 때 사용한 AI 앱이나 서비스의 이름을 알려주실 수 있나요?");
            case AI_PURPOSE -> List.of("AI로 도움을 얻고 싶었던 작업 한 가지를 알려주세요. 예를 들면 아이디어 구상이나 구도 비교가 있어요.",
                    "그 작업에서 AI를 쓰기 전 어떤 문제를 해결하려고 했나요? 특별한 목적이 없었다면 ‘없음’이라고 알려주세요.");
            case AI_OUTPUT_USAGE -> List.of("AI 결과는 아이디어만 참고했나요, 최종 작품에 일부 넣었나요, 아니면 사용하지 않았나요?",
                    "최종 작품에 AI 결과가 들어갔다면 해당 부분 하나를 알려주세요. 들어가지 않았다면 ‘최종 작품에 넣지 않았어요’라고 답해도 됩니다.");
            case HUMAN_CREATED_ELEMENT -> List.of("작품에서 처음부터 직접 만든 부분이 있다면 한 가지만 짚어 주세요. 없다면 ‘없음’이라고 알려주세요.",
                    "직접 구상하거나 그린 부분 하나와 그 부분을 만들 때 한 작업을 알려주세요. 해당 작업이 없다면 ‘없음’이라고 알려주세요.");
            case HUMAN_EDITED_EXPRESSION -> List.of("이미 있던 형태, 색, 배치 중 직접 바꾼 것이 있다면 한 가지만 알려주세요. 없다면 ‘없음’이라고 답해도 됩니다.",
                    "직접 바꾼 부분의 수정 전과 후가 어떻게 달랐나요? 바꾼 부분이 없다면 ‘없음’이라고 알려주세요.");
        };
    }

    /**
     * Question/answer pairs from related fields a new field may build on ("…라고 하셨는데"), oldest first. For each
     * related field its first answer (where work is usually described) and its last answer (the latest word, which
     * may correct the first) are always kept; remaining slots go to the most recent answers, so follow-ups in one
     * field cannot push another field's statements out. A "없음" answer is kept when it follows an earlier statement
     * of the same field, since it may correct it or only mean nothing more was done; the pair lets the model tell.
     * A field's opening "없음" carries nothing to build on and is left out.
     */
    static List<PreviousTurn> earlierTurns(Context context, InterviewField field) {
        var related = AI_FIELDS.contains(field) ? AI_FIELDS : HUMAN_CONTEXT;
        var stated = java.util.EnumSet.noneOf(InterviewField.class);
        var turns = new ArrayList<PreviousTurn>();
        for (var turn : context.turns()) {
            if (turn.field() == field || !related.contains(turn.field())) continue;
            boolean none = InterviewFieldState.isNotApplicableAnswer(turn.answer());
            if (none && !stated.contains(turn.field())) continue;
            if (!none) stated.add(turn.field());
            turns.add(turn);
        }
        var chosen = new java.util.TreeSet<Integer>();
        var first = new java.util.EnumMap<InterviewField, Integer>(InterviewField.class);
        var last = new java.util.EnumMap<InterviewField, Integer>(InterviewField.class);
        for (int i = 0; i < turns.size(); i++) {
            first.putIfAbsent(turns.get(i).field(), i);
            last.put(turns.get(i).field(), i);
        }
        // At most three related fields, so first and last answers fit within the six slots.
        chosen.addAll(first.values());
        chosen.addAll(last.values());
        for (int i = turns.size() - 1; i >= 0 && chosen.size() < EARLIER_TURNS; i--) chosen.add(i);
        return chosen.stream().map(turns::get).toList();
    }

    static boolean alreadyAsked(String question, List<String> previousQuestions) {
        String normalized = normalize(question);
        return previousQuestions.stream().map(InterviewQuestions::normalize).anyMatch(normalized::equals);
    }

    private static String normalize(String question) {
        var normalized = Normalizer.normalize(question, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return NON_WORD_CHARACTERS.matcher(normalized).replaceAll("");
    }
}
