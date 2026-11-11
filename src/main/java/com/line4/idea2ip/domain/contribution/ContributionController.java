package com.line4.idea2ip.domain.contribution;

import com.line4.idea2ip.domain.contribution.ContributionModels.*;
import com.line4.idea2ip.global.job.PersistentJob.Status;
import com.line4.idea2ip.global.jwt.CustomUserDetails;
import com.line4.idea2ip.global.response.SuccessResponse;
import com.line4.idea2ip.global.response.code.SuccessResponseCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/contributions")
@RequiredArgsConstructor
public class ContributionController {
    private final ContributionService service;

    @GetMapping
    public SuccessResponse<View> get(@AuthenticationPrincipal CustomUserDetails user, @PathVariable Long projectId) {
        return SuccessResponse.from(service.get(user.getUserId(), projectId));
    }

    @PostMapping("/generations")
    public ResponseEntity<SuccessResponse<GenerationView>> generate(@AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long projectId, @Valid @RequestBody GenerateReq req) {
        var result = service.generate(user.getUserId(), projectId, req);
        if (result.status() == Status.QUEUED || result.status() == Status.RUNNING) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(SuccessResponse.of(result, SuccessResponseCode.SUCCESS_ACCEPTED));
        }
        return ResponseEntity.ok(SuccessResponse.from(result));
    }

    @GetMapping("/steps/{stepId}")
    public SuccessResponse<Detail> detail(@AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long projectId, @PathVariable Long stepId) {
        return SuccessResponse.from(service.detail(user.getUserId(), projectId, stepId));
    }

    @PatchMapping
    public SuccessResponse<View> patch(@AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long projectId, @Valid @RequestBody PatchReq req) {
        return SuccessResponse.from(service.review(user.getUserId(), projectId, req));
    }
}
