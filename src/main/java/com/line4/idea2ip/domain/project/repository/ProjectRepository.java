package com.line4.idea2ip.domain.project.repository;

import com.line4.idea2ip.domain.project.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {
}
