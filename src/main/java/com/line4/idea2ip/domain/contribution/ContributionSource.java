package com.line4.idea2ip.domain.contribution;

import com.line4.idea2ip.integration.ai.BriefAiClient.Evidence;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "contribution_sources")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ContributionSource {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "step_id", nullable = false)
    private ContributionStep step;

    @Column(nullable = false, length = 96)
    private String sourceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Evidence evidence;

    ContributionSource(ContributionStep step, Evidence evidence) {
        this.step = step;
        this.sourceId = evidence.sourceId();
        this.evidence = evidence;
    }
}
