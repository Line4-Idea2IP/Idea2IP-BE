package com.line4.idea2ip.domain.project.repository;

import com.line4.idea2ip.domain.project.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findAllByUser_UserId(Long userId);
}
