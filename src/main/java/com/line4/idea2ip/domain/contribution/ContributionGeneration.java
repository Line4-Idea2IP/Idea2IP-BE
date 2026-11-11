package com.line4.idea2ip.domain.contribution;

import com.line4.idea2ip.global.entity.BaseEntity;
import com.line4.idea2ip.global.job.PersistentJob;
import com.line4.idea2ip.integration.ai.BriefAiClient.Input;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "contribution_generations", uniqueConstraints = @UniqueConstraint(
        name = "uk_contribution_generation_request", columnNames = {"review_id", "request_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ContributionGeneration extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false)
    private ContributionReview review;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(nullable = false)
    private long expectedRevision;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Input input;

    @Embedded
    private PersistentJob job = new PersistentJob();

    ContributionGeneration(ContributionReview review, UUID requestId, long expectedRevision, Input input) {
        this.review = review;
        this.requestId = requestId;
        this.expectedRevision = expectedRevision;
        this.input = input;
    }
}
