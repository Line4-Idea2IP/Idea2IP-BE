package com.line4.idea2ip.domain.contribution;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

interface ContributionReviewRepository extends JpaRepository<ContributionReview, Long> {
    Optional<ContributionReview> findByProject_ProjectId(Long projectId);
}
