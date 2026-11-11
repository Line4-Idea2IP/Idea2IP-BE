package com.line4.idea2ip.domain.report;

import com.line4.idea2ip.domain.report.ReportModels.*;
import com.line4.idea2ip.global.jwt.CustomUserDetails;
import com.line4.idea2ip.global.response.SuccessResponse;
import com.line4.idea2ip.global.response.code.SuccessResponseCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/report")
@RequiredArgsConstructor
public class ReportController {
    private final ReportService service;

    @GetMapping("/readiness")
    public SuccessResponse<Readiness> readiness(@AuthenticationPrincipal CustomUserDetails user, @PathVariable Long projectId) {
        return SuccessResponse.from(service.readiness(user.getUserId(), projectId));
    }

    @GetMapping
    public SuccessResponse<View> get(@AuthenticationPrincipal CustomUserDetails user, @PathVariable Long projectId) {
        return SuccessResponse.from(service.get(user.getUserId(), projectId));
    }

    @PostMapping("/versions")
    public ResponseEntity<SuccessResponse<Detail>> create(@AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long projectId, @Valid @RequestBody VersionReq req) {
        var result = service.create(user.getUserId(), projectId, req);
        if (result.status() == Status.QUEUED || result.status() == Status.RUNNING) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(SuccessResponse.of(result, SuccessResponseCode.SUCCESS_ACCEPTED));
        }
        return ResponseEntity.ok(SuccessResponse.from(result));
    }

    @GetMapping("/versions")
    public SuccessResponse<VersionList> list(@AuthenticationPrincipal CustomUserDetails user, @PathVariable Long projectId,
            @RequestParam(required = false) @Positive Integer beforeVersionNo, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return SuccessResponse.from(service.list(user.getUserId(), projectId, beforeVersionNo, limit));
    }

    @GetMapping("/versions/{versionId}")
    public SuccessResponse<Detail> version(@AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long projectId, @PathVariable Long versionId) {
        return SuccessResponse.from(service.getVersion(user.getUserId(), projectId, versionId));
    }

    @GetMapping("/versions/{versionId}/pdf")
    public ResponseEntity<byte[]> pdf(@AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long projectId, @PathVariable Long versionId) {
        var pdf = service.download(user.getUserId(), projectId, versionId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("copyright-brief-v" + pdf.versionNo() + ".pdf").build().toString())
                .body(pdf.bytes());
    }
}
