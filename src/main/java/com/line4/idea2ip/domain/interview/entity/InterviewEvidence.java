package com.line4.idea2ip.domain.interview.entity;

import com.line4.idea2ip.domain.project.entity.Project;
import com.line4.idea2ip.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One verified piece of evidence for an interview field: the user's own words and where they wrote them.
 * Rows come from interview answers ({@code sourceRef} "answer:12", {@code messageId} set) and, before the
 * interview, from the project and artwork descriptions ("project:3", "artwork:5"). The report cites answers from
 * here, so a sentence that settled a field it was not asked for is cited under that field.
 */
@Entity
@Table(name = "interview_evidence", indexes = @Index(name = "idx_interview_evidence_project", columnList = "project_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterviewEvidence extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long evidenceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 32)
    private InterviewField field;

    @Column(name = "source_ref", nullable = false, length = 60)
    private String sourceRef;

    /** The answer message the quote comes from; null for text written before the interview. */
    @Column(name = "message_id")
    private Long messageId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String quote;

    @Column(name = "input_revision", nullable = false)
    private long inputRevision;

    private InterviewEvidence(Project project, InterviewField field, String sourceRef, Long messageId, String quote,
                              long inputRevision) {
        this.project = project;
        this.field = field;
        this.sourceRef = sourceRef;
        this.messageId = messageId;
        this.quote = quote;
        this.inputRevision = inputRevision;
    }

    public static InterviewEvidence fromAnswer(Project project, InterviewField field, InterviewMessage answer, String quote) {
        if (answer.getRole() != InterviewMessage.Role.USER || !answer.getContent().contains(quote)) {
            throw new IllegalArgumentException("Expected a verbatim quote from a user answer");
        }
        return new InterviewEvidence(project, field, "answer:" + answer.getMessageId(), answer.getMessageId(), quote,
                project.getInputRevision());
    }

    public static InterviewEvidence fromInput(Project project, InterviewField field, String sourceRef, String quote) {
        return new InterviewEvidence(project, field, sourceRef, null, quote, project.getInputRevision());
    }
}
