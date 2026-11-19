package com.line4.idea2ip.integration.ai;

import com.line4.idea2ip.domain.interview.entity.InterviewField;
import com.line4.idea2ip.domain.interview.entity.InterviewFieldState;
import com.line4.idea2ip.domain.material.entity.MaterialType;

import java.util.List;
import java.util.Set;

public interface AiInterviewClient {
    InterviewFieldState analyzeAnswer(Context context, Answer answer);

    default AnswerAnalysis evaluate(Context context, Answer answer) {
        return new AnswerAnalysis(analyzeAnswer(context, answer), null, null);
    }

    /** Questions are built by code in every mode; see {@link InterviewQuestionTemplates}. */
    default QuestionDraft generateQuestion(Context context, InterviewField field) {
        return generateQuestion(context, field, null);
    }

    default QuestionDraft generateQuestion(Context context, InterviewField field, String clarification) {
        return InterviewQuestionTemplates.draft(context, field, clarification);
    }

    /**
     * What the latest answer also says about other fields, so they are not asked again. Only {@code open} fields
     * are considered, and a sentence already in {@code claimed} (the evidence of another field) never counts.
     * Each finding quotes the answer verbatim; the default finds nothing.
     */
    default List<Finding> findInAnswer(Context context, Answer answer, Set<InterviewField> open, List<String> claimed) {
        return List.of();
    }

    /** What the project's own descriptions already say about each field, before the interview starts. */
    default List<Finding> findInInputs(ProjectInputs inputs) {
        return List.of();
    }

    /**
     * Evidence for one field. {@code status} is COMPLETED, or NEEDS_REVIEW when the field is mentioned but a point
     * is missing; {@code gap} then says what. {@code quote} is a verbatim span of the source {@code sourceRef}
     * ("answer:12", "artwork:3", "material:5", "project:7").
     */
    record Finding(InterviewField field, InterviewFieldState.Status status, String quote, String sourceRef, String gap) {
    }

    /** Text the user wrote before the interview: project and artwork descriptions and material notes. */
    record ProjectInputs(Long projectId, List<InputText> texts) {
    }

    record InputText(String sourceRef, String label, String text) {
    }

    /**
     * Parts of the artwork named in the user's own descriptions ("철교", "세 칸 열차", "강의 반사광"), so questions
     * can ask about this artwork instead of any artwork. Each element is checked against the texts
     * ({@link ArtworkElements#verified}); the default finds none and questions stay generic.
     */
    default List<String> findElements(List<InputText> texts) {
        return List.of();
    }

    /** The final artwork as the user registered it, and the elements read from its descriptions. */
    record Work(String title, String description, List<String> elements) {
        public static final Work NONE = new Work(null, null, List.of());

        public Work {
            elements = elements == null ? List.of() : List.copyOf(elements);
        }
    }

    /** A null evidence quote leaves legacy/mock source handling in place; empty marks a live reply with no source. */
    /**
     * {@code confirmedName}: the user said "네" to a question naming the tool ("OpenAI의 Sora를 말씀하신 게 맞나요?"),
     * so {@code evidenceQuote} is that name rather than a quote of the answer.
     */
    record AnswerAnalysis(InterviewFieldState state, String evidenceQuote, String clarification, boolean confirmedName) {
        public AnswerAnalysis(InterviewFieldState state, String evidenceQuote, String clarification) {
            this(state, evidenceQuote, clarification, false);
        }
    }

    /**
     * {@code fields}: the field states when the context was taken (empty when unknown). {@code work}: the final
     * artwork, {@link Work#NONE} when unknown.
     */
    record Context(Long projectId, String title, String snapshotVersion,
                   List<Answer> answers, List<Evidence> evidence, List<AskedQuestion> questions,
                   List<PreviousTurn> turns, List<InterviewFieldState> fields, Work work) {
        public Context {
            work = work == null ? Work.NONE : work;
        }

        public Context(Long projectId, String title, String snapshotVersion, List<Answer> answers, List<Evidence> evidence,
                       List<AskedQuestion> questions, List<PreviousTurn> turns, List<InterviewFieldState> fields) {
            this(projectId, title, snapshotVersion, answers, evidence, questions, turns, fields, Work.NONE);
        }

        public Context(Long projectId, String title, String snapshotVersion, List<Answer> answers, List<Evidence> evidence,
                       List<AskedQuestion> questions, List<PreviousTurn> turns) {
            this(projectId, title, snapshotVersion, answers, evidence, questions, turns, List.of());
        }

        public Context(Long projectId, String title, String snapshotVersion, List<Answer> answers, List<Evidence> evidence) {
            this(projectId, title, snapshotVersion, answers, evidence, List.of(), List.of());
        }
    }

    /** One answered question, oldest first in {@link Context#turns()}. */
    record PreviousTurn(InterviewField field, String question, String answer) {
    }

    record AskedQuestion(InterviewField field, String content) {
    }

    /** question: text of the question this answers (null when unknown); used to recognise closing prompts. */
    record Answer(Long messageId, InterviewField field, String content, String question,
                  String evidenceQuote, String questionReason) {
        public Answer(Long messageId, InterviewField field, String content, String question, String evidenceQuote) {
            this(messageId, field, content, question, evidenceQuote, null);
        }

        public Answer(Long messageId, InterviewField field, String content, String question) {
            this(messageId, field, content, question, null);
        }

        public Answer(Long messageId, InterviewField field, String content) {
            this(messageId, field, content, null);
        }
    }

    record Evidence(String sourceId, MaterialType type, String title, String description, String usedAi) {
    }

    record QuestionDraft(String content, String reason, List<String> sourceIds) {
    }
}
