package com.line4.idea2ip.domain.interview.repository;

import com.line4.idea2ip.domain.interview.entity.InterviewPrefill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InterviewPrefillRepository extends JpaRepository<InterviewPrefill, Long> {
    Optional<InterviewPrefill> findByProject_ProjectId(Long projectId);
}
