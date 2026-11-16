package com.line4.idea2ip.domain.interview.entity;

import com.line4.idea2ip.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(name = "interview_messages", uniqueConstraints = {
        @UniqueConstraint(name = "uk_interview_message_sequence", columnNames = {"interview_id", "sequence_no"}),
        @UniqueConstraint(name = "uk_interview_answer_request", columnNames = {"interview_id", "request_id"}),
        @UniqueConstraint(name = "uk_interview_answer_question", columnNames = {"reply_to_question_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterviewMessage extends BaseEntity {
    public enum Role {
        ASSISTANT,
        USER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long messageId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "interview_id", nullable = false)
    private AiInterview interview;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private Role role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 32)
    private InterviewField targetField;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_question_id")
    private InterviewMessage replyToQuestion;

    @Column(name = "request_id", length = 36)
    private String requestId;

    @Column(length = 500)
    private String reason;

    @Column(name = "validated_evidence_quote", columnDefinition = "TEXT")
    private String validatedEvidenceQuote;

    // Null means the answer still needs evaluation. An empty string means no clarification is needed.
    @Column(name = "evaluation_clarification", length = 200)
    private String evaluationClarification;

    public void recordEvaluation(String quote, String clarification) {
        if (role != Role.USER || evaluationClarification != null) {
            throw new IllegalStateException("Only a pending user answer can be evaluated");
        }
        if (quote != null && !content.contains(quote)) {
            throw new IllegalArgumentException("Expected a verbatim quote from this answer");
        }
        if (clarification != null && clarification.length() > 200) {
            throw new IllegalArgumentException("Clarification exceeds the stored length");
        }
        validatedEvidenceQuote = quote;
        evaluationClarification = clarification == null ? "" : clarification;
    }

    /**
     * Narrows the recorded quote to a part of itself, once the rest of the answer turned out to be evidence for
     * other fields: an output-use answer that also says "사과 단면은 직접 그렸고" keeps only its output-use sentences.
     */
    public void narrowEvidence(String quote) {
        if (role != Role.USER || validatedEvidenceQuote == null || quote == null || quote.isBlank()
                || !validatedEvidenceQuote.contains(quote)) {
            throw new IllegalArgumentException("Expected a part of the recorded quote");
        }
        validatedEvidenceQuote = quote;
    }

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> sourceIds;

    private InterviewMessage(AiInterview interview, int sequenceNo, Role role, String content,
                             InterviewField targetField, InterviewMessage replyToQuestion, String requestId,
                             String reason, List<String> sourceIds) {
        this.interview = interview;
        this.sequenceNo = sequenceNo;
        this.role = role;
        this.content = content;
        this.targetField = targetField;
        this.replyToQuestion = replyToQuestion;
        this.requestId = requestId;
        this.reason = reason;
        this.sourceIds = List.copyOf(sourceIds);
    }

    public static InterviewMessage question(AiInterview interview, int sequenceNo, InterviewField field,
                                            String content, String reason, List<String> sourceIds) {
        return new InterviewMessage(interview, sequenceNo, Role.ASSISTANT, content, field, null, null, reason, sourceIds);
    }

    public static InterviewMessage answer(InterviewMessage question, String requestId, String content) {
        return new InterviewMessage(question.interview, question.sequenceNo + 1, Role.USER, content,
                question.targetField, question, requestId, null, List.of());
    }
}
