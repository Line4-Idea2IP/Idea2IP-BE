package com.line4.idea2ip.domain.contribution;

import com.line4.idea2ip.global.job.PersistentJob.Status;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ContributionGenerationRepository extends JpaRepository<ContributionGeneration, Long> {
    Optional<ContributionGeneration> findByReview_IdAndRequestId(Long reviewId, UUID requestId);
    Optional<ContributionGeneration> findFirstByReview_IdOrderByUpdateAtDescIdDesc(Long reviewId);
    boolean existsByReview_IdAndJob_StatusIn(Long reviewId, List<Status> statuses);

    @Query("""
            select g.id from ContributionGeneration g
            where g.job.status = :queued or (g.job.status = :running and g.job.leaseUntil <= :now)
            order by g.id
            """)
    List<Long> findRunnable(@Param("queued") Status queued, @Param("running") Status running,
                            @Param("now") Instant now, Limit limit);

    @Query("select g.review.project.projectId from ContributionGeneration g where g.id = :id")
    Optional<Long> projectId(@Param("id") Long id);
}
