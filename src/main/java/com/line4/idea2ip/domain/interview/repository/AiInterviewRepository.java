package com.line4.idea2ip.domain.interview.repository;

import com.line4.idea2ip.domain.interview.entity.AiInterview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AiInterviewRepository extends JpaRepository<AiInterview, Long> {
    Optional<AiInterview> findByProject_ProjectId(Long projectId);

    @Query("""
            select i.project.projectId as projectId, i.status as status
            from AiInterview i where i.project.projectId in :projectIds
            """)
    List<ProjectInterviewStatus> findProjectStatuses(@Param("projectIds") List<Long> projectIds);

    interface ProjectInterviewStatus {
        Long getProjectId();
        AiInterview.Status getStatus();
    }
}
