package com.line4.idea2ip.integration.ai;

import com.line4.idea2ip.domain.interview.entity.InterviewField;
import com.line4.idea2ip.domain.interview.entity.InterviewFieldState;
import com.line4.idea2ip.domain.interview.entity.NoAiAnswer;
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
        return followUp(field, asked, supplement, null);
    }

    /** As above, with the supplement quoting {@code previousAnswer} (the field's latest answer) when given. */
    static String followUp(InterviewField field, List<String> asked, Optional<InterviewSupplement> supplement,
                           String previousAnswer) {
        if (field == InterviewField.AI_TOOL && NoAiAnswer.unclearNone(previousAnswer)
                && !alreadyAsked(NO_AI_CONFIRMATION, asked)) {
            // "업슴", "아니요": settling AI_TOOL as not applicable skips the other AI questions, so confirm it first.
            return NO_AI_CONFIRMATION;
        }
        if (supplement.isPresent()) {
            String question = supplement.get().questionFor(previousAnswer);
            if (!alreadyAsked(question, asked)) return question;
        }
        return unused(field, alternatives(field).getFirst(), asked);
    }

    static final String OPENING_REASON = "앞선 답변에서 말씀하신 작업을 구체적으로 확인합니다.";
    private static final List<String> EDIT_WORDS = List.of("수정", "바꾸", "바꿨", "바꾼", "고쳤", "고치", "칠했", "칠하",
            "칠한", "변경", "다듬", "보정", "덧그", "추가했");
    private static final List<String> CREATE_WORDS = List.of("직접 그", "그렸", "그린", "만들었", "만든", "구성했", "창작",
            "구상했", "스케치");
    private static final Set<String> FIRST_PERSON = Set.of("제가", "내가", "저는", "나는", "저희가", "저희는", "우리가", "우리는");
    private static final Pattern SUBJECT_OR_TOPIC = Pattern.compile(
            "(?<![\\p{L}\\p{N}])[\\p{L}\\p{N}]+(?:께서|[가이은는])(?![\\p{L}\\p{N}])");
    // Things in or about the artwork; as a subject ("시안이 마음에 들어") they are not someone else doing the work.
    private static final Pattern ARTWORK_NOUN = Pattern.compile(
            "(?:작품|그림|구도|배치|위치|크기|표정|눈|입|색|색상|색감|형태|모양|시계판|부분|요소|이미지|시안|결과|결과물|"
                    + "배경|선|질감|조명|각도|손|손목|사슬|옷|머리|머리카락|캐릭터|그림자|테두리|글자|로고|패턴|장갑|"
                    + "초안|스케치|레이어|비율|명암|채도|밝기|톤)(?:께서|[가이은는])$");
    // Adverbs that end like a subject marker but are not subjects.
    private static final Set<String> ADVERBS = Set.of("많이", "같이", "깊이", "높이", "일일이", "굳이", "깨끗이", "새로이",
            "따로이", "가까이", "고이", "틈틈이", "반듯이");
    private static final Pattern OTHER_AGENT = Pattern.compile(
            "(AI|에이아이|모델|프로그램|툴|작가|디자이너|외주(업체)?|친구|동료|팀원|선생님|다른 사람|누군가)"
                    + "(가|이|는|은|께서|에게서|이랑|랑|와|과|로|으로|를 통해|을 통해)(?![가-힣A-Za-z])");
    private static final List<String> PASSIVE = List.of("됐", "되었", "되어", "돼");
    private static final List<String> NEGATION = List.of("않", "없", "안 했", "못 ");
    // "안 칠했어요", "안고쳤어요": 안 before a verb negates it ("안에", "안쪽" are places, not negation).
    private static final Pattern NEGATED_VERB = Pattern.compile(
            "(?<![가-힣])안\\s+[가-힣]|(?<![가-힣])안(?:칠|고치|고쳤|바꾸|바꿨|그렸|그리|만들|만든|수정|건드|다듬|넣)");
    // Topics that set time or place ("처음에는", "화면에서는") rather than name an actor.
    private static final Pattern ADVERBIAL_TOPIC = Pattern.compile("(?:에는|에서는|에선|으로는|로는|때는|땐)$");
    private static final List<String> CORRECTION = List.of("정정", "잘못 말", "취소", "사실은", "사실 ", "아니라", "아니었");
    private static final Pattern SENTENCE_END = Pattern.compile("[.!?。\\n]+");
    private static final Pattern CLAUSE_BREAK = Pattern.compile("(?<=고)\\s+|,\\s*");

    /**
     * A clause from a related earlier answer where the user described their own work for this human field, e.g.
     * "색상과 표정은 직접 수정했어요" said while describing AI output use; most recent first. A field whose
     * statement is later followed by "없음", a negated statement of the same work, or a correction phrase
     * ("정정할게요") is treated as unresolved and not used, so an ambiguous correction leads to a neutral question.
     */
    static Optional<String> relatedStatement(Context context, InterviewField field) {
        return related(context, field).map(Related::statement);
    }

    /**
     * A related statement and whether it clearly describes the user's own work. When a topic could name another
     * person ("민수는 색을 직접 칠했어요"), {@code ownWork} is false and the opening asks who did it first.
     */
    record Related(String statement, boolean ownWork) {}

    static Optional<Related> related(Context context, InterviewField field) {
        List<String> words = workWords(field);
        if (words.isEmpty()) return Optional.empty();
        var statements = new java.util.EnumMap<InterviewField, List<Related>>(InterviewField.class);
        var unresolved = java.util.EnumSet.noneOf(InterviewField.class);
        var order = new ArrayList<InterviewField>();
        for (var turn : earlierTurns(context, field)) {
            var said = statements.computeIfAbsent(turn.field(), ignored -> new ArrayList<>());
            boolean doubted = InterviewFieldState.isNotApplicableAnswer(turn.answer())
                    || CORRECTION.stream().anyMatch(turn.answer()::contains);
            var found = new ArrayList<Related>();
            for (String sentence : SENTENCE_END.split(turn.answer())) {
                boolean otherActor = false;
                boolean uncertainActor = false;
                for (String part : CLAUSE_BREAK.split(sentence)) {
                    String clause = part.strip();
                    var subjects = SUBJECT_OR_TOPIC.matcher(clause);
                    boolean firstPerson = false;
                    boolean unclearTopic = false;
                    boolean otherSubject = OTHER_AGENT.matcher(clause).find();
                    while (subjects.find()) {
                        String subject = subjects.group();
                        boolean partOfWork = ADVERBS.contains(subject) || ARTWORK_NOUN.matcher(subject).find();
                        if (FIRST_PERSON.contains(subject)) {
                            firstPerson = true;
                        } else if (nominative(subject) && !partOfWork) {
                            // "민수가", "선생님께서": a named actor.
                            otherSubject = true;
                        } else if (!nominative(subject) && !partOfWork && !ADVERBIAL_TOPIC.matcher(subject).find()) {
                            // "민수는" or "포즈는": a person or a part of the work; ask who did it rather than assume.
                            unclearTopic = true;
                        }
                    }
                    // A connected clause can inherit the preceding actor or uncertainty about that actor.
                    otherActor = otherSubject || (otherActor && !firstPerson);
                    uncertainActor = !firstPerson && (uncertainActor || unclearTopic);
                    if (words.stream().noneMatch(clause::contains)) continue;
                    if (negated(clause)) doubted = true;
                    else if (!otherActor && (firstPerson || clause.contains("직접") || clause.contains("손으로"))
                            && PASSIVE.stream().noneMatch(clause::contains)) {
                        found.add(new Related(trim(endClause(clause)), !uncertainActor));
                    }
                }
            }
            // Doubt only matters after something was said; a mixed first answer keeps its positive clauses.
            if (doubted && !said.isEmpty()) unresolved.add(turn.field());
            said.addAll(found);
            if (!found.isEmpty()) order.add(turn.field());
        }
        for (var source : order.reversed()) {
            if (unresolved.contains(source)) continue;
            var said = statements.get(source);
            return Optional.of(said.getLast());
        }
        return Optional.empty();
    }

    private static boolean negated(String clause) {
        return NEGATION.stream().anyMatch(clause::contains) || NEGATED_VERB.matcher(clause).find();
    }

    /** Subject marker 가/이/께서, as opposed to the topic marker 은/는. */
    private static boolean nominative(String word) {
        return word.endsWith("께서") || word.endsWith("가") || word.endsWith("이");
    }

    static boolean mentionsRelatedWork(Context context, InterviewField field) {
        List<String> words = workWords(field);
        return !words.isEmpty() && earlierTurns(context, field).stream()
                .anyMatch(turn -> words.stream().anyMatch(turn.answer()::contains));
    }

    private static List<String> workWords(InterviewField field) {
        return switch (field) {
            case HUMAN_EDITED_EXPRESSION -> EDIT_WORDS;
            case HUMAN_CREATED_ELEMENT -> CREATE_WORDS;
            case AI_TOOL, AI_PURPOSE, AI_OUTPUT_USAGE -> List.of();
        };
    }

    /** "색은 직접 수정했고" (cut before "…고 표정은") reads as a full statement when quoted: "색은 직접 수정했다". */
    private static String endClause(String clause) {
        return clause.matches(".*[했었았였]고") ? clause.substring(0, clause.length() - 1) + "다" : clause;
    }

    /**
     * Opening question about the quoted work only. It does not also ask whether there was other work, so a bare
     * "없음" in reply cannot be mistaken for "no such work at all" (see {@link #isOpeningQuestion}).
     */
    static String opening(InterviewField field, Related related) {
        if (related.ownWork()) return opening(field, related.statement());
        return switch (field) {
            case HUMAN_EDITED_EXPRESSION -> "‘" + related.statement() + OPENING_EDIT_WHO_TAIL;
            case HUMAN_CREATED_ELEMENT -> "‘" + related.statement() + OPENING_CREATE_WHO_TAIL;
            case AI_TOOL, AI_PURPOSE, AI_OUTPUT_USAGE -> MockAiInterviewClient.baseQuestion(field);
        };
    }

    static String opening(InterviewField field, String statement) {
        return switch (field) {
            case HUMAN_EDITED_EXPRESSION -> "‘" + statement + OPENING_EDIT_TAIL;
            case HUMAN_CREATED_ELEMENT -> "‘" + statement + OPENING_CREATE_TAIL;
            case AI_TOOL, AI_PURPOSE, AI_OUTPUT_USAGE -> MockAiInterviewClient.baseQuestion(field);
        };
    }

    private static final String OPENING_EDIT_TAIL = "’라고 하셨는데, 어떤 부분을 어떻게 바꾸셨나요?";
    private static final String OPENING_CREATE_TAIL = "’라고 하셨는데, 그 요소를 어떤 방식으로 만드셨나요?";
    private static final String OPENING_EDIT_WHO_TAIL =
            "’라고 하셨는데, 이 수정은 직접 하신 건가요? 직접 하셨다면 어떤 부분을 어떻게 바꾸셨는지 알려주세요.";
    private static final String OPENING_CREATE_WHO_TAIL =
            "’라고 하셨는데, 이 요소는 직접 만드신 건가요? 직접 만드셨다면 어떤 방식으로 만드셨는지 알려주세요.";

    /** Whether a question is an opening built on an earlier statement for this field. */
    static boolean isOpeningQuestion(InterviewField field, String question) {
        if (question == null || !question.startsWith("‘")) return false;
        return switch (field) {
            case HUMAN_EDITED_EXPRESSION -> question.endsWith(OPENING_EDIT_TAIL) || question.endsWith(OPENING_EDIT_WHO_TAIL);
            case HUMAN_CREATED_ELEMENT -> question.endsWith(OPENING_CREATE_TAIL) || question.endsWith(OPENING_CREATE_WHO_TAIL);
            case AI_TOOL, AI_PURPOSE, AI_OUTPUT_USAGE -> false;
        };
    }

    /** The quoted statement of an opening question, e.g. "색상과 표정은 직접 수정했어요". */
    static String openingStatement(String question) {
        int end = question.indexOf("’라고 하셨는데");
        return end > 1 ? question.substring(1, end) : question;
    }

    /** The first sentence of an answer for quoting, without its final punctuation and at most 50 characters. */
    static String quote(String answer) {
        if (answer == null) return "";
        for (String sentence : SENTENCE_END.split(answer)) {
            if (!sentence.isBlank()) return trim(sentence.strip());
        }
        return "";
    }

    private static String trim(String text) {
        String value = text.replaceAll("[\\s.!?,~]+$", "");
        if (value.codePointCount(0, value.length()) <= 50) return value;
        return value.substring(0, value.offsetByCodePoints(0, 50)) + "…";
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
    static final String NO_AI_CONFIRMATION =
            "AI 도구를 사용하지 않았다는 뜻인가요? 맞다면 ‘네’라고, 사용했다면 도구 이름을 알려주세요.";
    static final String NO_AI_CONFIRMATION_REASON = "AI 도구를 사용하지 않았는지 확인합니다.";
    static final String NO_AI_CONFIRMED = "AI 도구를 사용하지 않았다고 확인했습니다.";

    static boolean isNoAiConfirmation(String question) {
        return question != null && normalize(question).equals(normalize(NO_AI_CONFIRMATION));
    }

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
