package com.line4.idea2ip.domain.material.controller;

import com.line4.idea2ip.domain.material.dto.MaterialCreateReq;
import com.line4.idea2ip.domain.material.dto.MaterialCreateRes;
import com.line4.idea2ip.domain.material.dto.MaterialListRes;
import com.line4.idea2ip.domain.material.service.CreativeMaterialService;
import com.line4.idea2ip.global.jwt.CustomUserDetails;
import com.line4.idea2ip.global.response.SuccessResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectMaterialController {

    private final CreativeMaterialService materialService;

    @PostMapping(value = "/{projectId}/materials", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SuccessResponse<MaterialCreateRes>> createMaterial(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody MaterialCreateReq req
    ) {
        MaterialCreateRes response = materialService.createMaterial(userDetails.getUserId(), projectId, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(SuccessResponse.created(response));
    }

    @GetMapping("/{projectId}/materials")
    public ResponseEntity<SuccessResponse<MaterialListRes>> getMaterials(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("projectId") Long projectId
    ) {
        MaterialListRes response = materialService.getMaterials(userDetails.getUserId(), projectId);
        return ResponseEntity.ok(SuccessResponse.from(response));
    }
}
