package com.line4.idea2ip.domain.project.controller;

import com.line4.idea2ip.domain.project.dto.ProjectCreateReq;
import com.line4.idea2ip.domain.project.dto.ProjectCreateRes;
import com.line4.idea2ip.domain.project.dto.ProjectListRes;
import com.line4.idea2ip.domain.project.service.ProjectService;
import com.line4.idea2ip.global.jwt.CustomUserDetails;
import com.line4.idea2ip.global.response.SuccessResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    public ResponseEntity<SuccessResponse<ProjectCreateRes>> createProject(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ProjectCreateReq req
    ) {
        ProjectCreateRes response = projectService.createProject(userDetails.getUserId(), req);
        return ResponseEntity.status(HttpStatus.CREATED).body(SuccessResponse.created(response));
    }

    @GetMapping
    public ResponseEntity<SuccessResponse<ProjectListRes>> getMyProjects(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        ProjectListRes response = projectService.getMyProjects(userDetails.getUserId());
        return ResponseEntity.ok(SuccessResponse.from(response));
    }
}
