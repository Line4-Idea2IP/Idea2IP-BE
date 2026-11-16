package com.line4.idea2ip.domain.interview.repository;

import com.line4.idea2ip.domain.interview.entity.InterviewEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InterviewEvidenceRepository extends JpaRepository<InterviewEvidence, Long> {
    List<InterviewEvidence> findAllByProject_ProjectIdOrderByEvidenceIdAsc(Long projectId);

    /** Removes evidence read from descriptions before the interview, so a newer revision can replace it. */
    @Modifying
    @Query("delete from InterviewEvidence e where e.project.projectId = :projectId and e.messageId is null")
    void deleteInputEvidence(@Param("projectId") Long projectId);
}
