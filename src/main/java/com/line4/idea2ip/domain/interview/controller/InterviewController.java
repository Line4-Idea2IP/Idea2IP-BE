package com.line4.idea2ip.domain.interview.controller;

import com.line4.idea2ip.domain.interview.dto.InterviewAnswerReq;
import com.line4.idea2ip.domain.interview.dto.InterviewResponses.*;
import com.line4.idea2ip.domain.interview.service.InterviewService;
import com.line4.idea2ip.global.jwt.CustomUserDetails;
import com.line4.idea2ip.global.response.SuccessResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/interview")
@RequiredArgsConstructor
public class InterviewController {
    private final InterviewService interviewService;

    @GetMapping("/readiness")
    public ResponseEntity<SuccessResponse<ReadinessRes>> getReadiness(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable("projectId") Long projectId
    ) {
        return ResponseEntity.ok(SuccessResponse.from(interviewService.getReadiness(user.getUserId(), projectId)));
    }

    @PostMapping
    public ResponseEntity<SuccessResponse<InterviewStateRes>> start(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable("projectId") Long projectId
    ) {
        var started = interviewService.start(user.getUserId(), projectId);
        if (started.created()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(SuccessResponse.created(started.interview()));
        }
        return ResponseEntity.ok(SuccessResponse.from(started.interview()));
    }

    @GetMapping
    public ResponseEntity<SuccessResponse<InterviewStateRes>> getInterview(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable("projectId") Long projectId
    ) {
        return ResponseEntity.ok(SuccessResponse.from(interviewService.getInterview(user.getUserId(), projectId)));
    }

    @GetMapping("/messages")
    public ResponseEntity<SuccessResponse<MessagesRes>> getMessages(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable("projectId") Long projectId
    ) {
        return ResponseEntity.ok(SuccessResponse.from(interviewService.getMessages(user.getUserId(), projectId)));
    }

    @PostMapping("/answers")
    public ResponseEntity<SuccessResponse<AnswerRes>> submitAnswer(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable("projectId") Long projectId,
            @Valid @RequestBody InterviewAnswerReq req
    ) {
        return ResponseEntity.ok(SuccessResponse.from(interviewService.submitAnswer(user.getUserId(), projectId, req)));
    }
}
