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
