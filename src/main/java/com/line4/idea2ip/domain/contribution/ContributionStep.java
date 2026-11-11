package com.line4.idea2ip.domain.contribution;

import com.line4.idea2ip.domain.contribution.ContributionModels.Content;
import com.line4.idea2ip.domain.contribution.ContributionModels.Decision;
import com.line4.idea2ip.domain.contribution.ContributionModels.Kind;
import com.line4.idea2ip.domain.contribution.ContributionModels.StepStatus;
import com.line4.idea2ip.integration.ai.BriefAiClient.Evidence;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "contribution_steps", uniqueConstraints = @UniqueConstraint(
        name = "uk_contribution_step_resource", columnNames = {"review_id", "kind", "resource_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ContributionStep {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false)
    private ContributionReview review;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Kind kind;

    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    @Column(nullable = false, columnDefinition = "text")
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StepStatus status = StepStatus.UNREVIEWED;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Content content = Content.empty();

    @OneToMany(mappedBy = "step", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<ContributionSource> sources = new ArrayList<>();

    ContributionStep(ContributionReview review, Kind kind, Long resourceId, String title) {
        this.review = review;
        this.kind = kind;
        this.resourceId = resourceId;
        this.title = title;
    }

    boolean propose(Content proposal, List<Evidence> evidence) {
        if (status != StepStatus.UNREVIEWED || (content.equals(proposal) && evidence().equals(evidence))) {
            return false;
        }
        content = proposal;
        sources.clear();
        evidence.forEach(item -> sources.add(new ContributionSource(this, item)));
        return true;
    }

    boolean apply(Decision decision, Content edits) {
        var nextStatus = switch (decision) {
            case EDIT -> StepStatus.EDITED;
            case CONFIRM -> StepStatus.CONFIRMED;
            case EXCLUDE -> StepStatus.EXCLUDED;
        };
        var nextContent = content.apply(edits);
        boolean changed = status != nextStatus || !content.equals(nextContent);
        status = nextStatus;
        content = nextContent;
        return changed;
    }

    List<Evidence> evidence() { return sources.stream().map(ContributionSource::getEvidence).toList(); }

    boolean needsReview() { return status == StepStatus.EDITED || (status == StepStatus.UNREVIEWED && content.hasContribution()); }
}
