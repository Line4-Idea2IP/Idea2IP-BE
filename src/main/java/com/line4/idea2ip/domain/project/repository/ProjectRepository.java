package com.line4.idea2ip.domain.project.repository;

import com.line4.idea2ip.domain.project.entity.Project;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findAllByUser_UserIdOrderByUpdateAtDescProjectIdDesc(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Project p where p.projectId = :projectId")
    Optional<Project> findByIdForUpdate(@Param("projectId") Long projectId);
}
