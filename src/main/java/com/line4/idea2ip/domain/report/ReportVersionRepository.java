package com.line4.idea2ip.domain.report;

import com.line4.idea2ip.global.job.PersistentJob.Status;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ReportVersionRepository extends JpaRepository<ReportVersion, Long> {
    Optional<ReportVersion> findByReport_IdAndRequestId(Long reportId, UUID requestId);
    Optional<ReportVersion> findByReport_Project_ProjectIdAndId(Long projectId, Long id);
    Optional<ReportVersion> findFirstByReport_IdAndJob_StatusInOrderByVersionNoDesc(Long reportId, List<Status> statuses);
    Optional<ReportVersion> findFirstByReport_IdAndJob_StatusOrderByVersionNoDesc(Long reportId, Status status);
    List<ReportVersion> findByReport_IdOrderByVersionNoDesc(Long reportId, Limit limit);
    List<ReportVersion> findByReport_IdAndVersionNoLessThanOrderByVersionNoDesc(Long reportId, int versionNo, Limit limit);

    @Query("""
            select v.id from ReportVersion v
            where v.job.status = :queued or (v.job.status = :running and v.job.leaseUntil <= :now)
            order by v.id
            """)
    List<Long> findRunnable(@Param("queued") Status queued, @Param("running") Status running,
                            @Param("now") Instant now, Limit limit);

    @Query("select v.report.project.projectId from ReportVersion v where v.id = :id")
    Optional<Long> projectId(@Param("id") Long id);

    @Query("select distinct v.report.id from ReportVersion v where v.report.id in :reportIds and v.job.status in :statuses")
    List<Long> reportIdsWithStatuses(@Param("reportIds") List<Long> reportIds, @Param("statuses") List<Status> statuses);
}
