package com.line4.idea2ip.domain.contribution;

import com.line4.idea2ip.domain.contribution.ContributionModels.ReviewStatus;
import com.line4.idea2ip.domain.project.entity.Project;
import com.line4.idea2ip.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(name = "contribution_reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ContributionReview extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, unique = true)
    private Project project;

    @Column(nullable = false)
    private long revision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReviewStatus status = ReviewStatus.NOT_STARTED;

    @Column(columnDefinition = "text")
    private String noContributionReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> unresolvedFields = List.of();

    ContributionReview(Project project) { this.project = project; }

    void generated(boolean hasChanges, List<String> unresolved) {
        if (status == ReviewStatus.NOT_STARTED || hasChanges || !unresolvedFields.equals(unresolved)) {
            revision++;
            if (hasChanges || status == ReviewStatus.NOT_STARTED) {
                status = ReviewStatus.IN_REVIEW;
                noContributionReason = null;
            }
            unresolvedFields = List.copyOf(unresolved);
        }
    }

    void review(boolean finalized, String reason) {
        status = finalized ? ReviewStatus.CONFIRMED : ReviewStatus.IN_REVIEW;
        noContributionReason = reason;
        revision++;
    }
}
