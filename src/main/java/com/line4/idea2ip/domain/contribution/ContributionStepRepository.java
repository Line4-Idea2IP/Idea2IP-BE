package com.line4.idea2ip.domain.contribution;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

interface ContributionStepRepository extends JpaRepository<ContributionStep, Long> {
    @EntityGraph(attributePaths = "sources")
    List<ContributionStep> findByReview_IdOrderById(Long reviewId);
}
