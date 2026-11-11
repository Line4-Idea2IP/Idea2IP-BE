package com.line4.idea2ip.domain.report;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

interface ReportRepository extends JpaRepository<Report, Long> {
    @EntityGraph(attributePaths = "latestReadyVersion")
    Optional<Report> findByProject_ProjectId(Long projectId);

    @EntityGraph(attributePaths = "latestReadyVersion")
    List<Report> findByProject_ProjectIdIn(List<Long> projectIds);
}
