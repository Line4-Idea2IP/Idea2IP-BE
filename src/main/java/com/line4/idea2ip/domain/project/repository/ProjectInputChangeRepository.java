package com.line4.idea2ip.domain.project.repository;

import com.line4.idea2ip.domain.project.entity.ProjectInputChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectInputChangeRepository extends JpaRepository<ProjectInputChange, Long> {
    List<ProjectInputChange> findByProject_ProjectIdAndRevisionGreaterThanAndRevisionLessThanEqualOrderByRevision(
            Long projectId, long fromRevision, long toRevision);
}
