package com.line4.idea2ip.domain.interview.repository;

import com.line4.idea2ip.domain.interview.entity.AiInterview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiInterviewRepository extends JpaRepository<AiInterview, Long> {
    Optional<AiInterview> findByProject_ProjectId(Long projectId);
}
