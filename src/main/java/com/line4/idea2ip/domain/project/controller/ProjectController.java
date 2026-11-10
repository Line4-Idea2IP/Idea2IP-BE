package com.line4.idea2ip.domain.project.controller;

import com.line4.idea2ip.domain.project.dto.ProjectCreateReq;
import com.line4.idea2ip.domain.project.dto.ProjectCreateRes;
import com.line4.idea2ip.domain.project.dto.ProjectDetailRes;
import com.line4.idea2ip.domain.project.dto.ProjectListRes;
import com.line4.idea2ip.domain.project.dto.ProjectThumbnailRes;
import com.line4.idea2ip.domain.project.service.ProjectService;
import com.line4.idea2ip.global.jwt.CustomUserDetails;
import com.line4.idea2ip.global.response.SuccessResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SuccessResponse<ProjectCreateRes>> createProject(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ProjectCreateReq req
    ) {
        ProjectCreateRes response = projectService.createProject(userDetails.getUserId(), req);
        return ResponseEntity.status(HttpStatus.CREATED).body(SuccessResponse.created(response));
    }

    @PatchMapping(value = "/{projectId}/thumbnail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuccessResponse<ProjectThumbnailRes>> updateThumbnail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("projectId") Long projectId,
            @RequestParam("thumbnail") MultipartFile thumbnail
    ) {
        ProjectThumbnailRes response = projectService.updateThumbnail(userDetails.getUserId(), projectId, thumbnail);
        return ResponseEntity.ok(SuccessResponse.from(response));
    }

    @GetMapping
    public ResponseEntity<SuccessResponse<ProjectListRes>> getMyProjects(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        ProjectListRes response = projectService.getMyProjects(userDetails.getUserId());
        return ResponseEntity.ok(SuccessResponse.from(response));
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<SuccessResponse<ProjectDetailRes>> getProjectDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("projectId") Long projectId
    ) {
        ProjectDetailRes response = projectService.getProjectDetail(userDetails.getUserId(), projectId);
        return ResponseEntity.ok(SuccessResponse.from(response));
    }
}
