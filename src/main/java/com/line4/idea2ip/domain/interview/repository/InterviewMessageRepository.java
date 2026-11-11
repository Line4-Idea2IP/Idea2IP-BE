package com.line4.idea2ip.domain.interview.repository;

import com.line4.idea2ip.domain.interview.entity.InterviewMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InterviewMessageRepository extends JpaRepository<InterviewMessage, Long> {
    List<InterviewMessage> findAllByInterview_InterviewIdOrderBySequenceNo(Long interviewId);

    Optional<InterviewMessage> findFirstByInterview_InterviewIdOrderBySequenceNoDesc(Long interviewId);

    Optional<InterviewMessage> findByInterview_InterviewIdAndMessageId(Long interviewId, Long messageId);

    Optional<InterviewMessage> findByInterview_InterviewIdAndRequestId(Long interviewId, String requestId);
}
