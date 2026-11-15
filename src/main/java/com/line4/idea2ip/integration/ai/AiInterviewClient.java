package com.line4.idea2ip.integration.ai;

import com.line4.idea2ip.domain.interview.entity.InterviewField;
import com.line4.idea2ip.domain.interview.entity.InterviewFieldState;
import com.line4.idea2ip.domain.material.entity.MaterialType;

import java.util.List;

public interface AiInterviewClient {
    QuestionDraft generateQuestion(Context context, InterviewField field);

    InterviewFieldState analyzeAnswer(Context context, Answer answer);

    default AnswerAnalysis evaluate(Context context, Answer answer) {
        return new AnswerAnalysis(analyzeAnswer(context, answer), null, null);
    }

    default QuestionDraft generateQuestion(Context context, InterviewField field, String clarification) {
        return generateQuestion(context, field);
    }

    /** A null evidence quote leaves legacy/mock source handling in place; empty marks a live reply with no source. */
    record AnswerAnalysis(InterviewFieldState state, String evidenceQuote, String clarification) {}

    record Context(Long projectId, String title, String snapshotVersion,
                   List<Answer> answers, List<Evidence> evidence, List<AskedQuestion> questions,
                   List<PreviousTurn> turns) {
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
    record Answer(Long messageId, InterviewField field, String content, String question, String evidenceQuote) {
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
