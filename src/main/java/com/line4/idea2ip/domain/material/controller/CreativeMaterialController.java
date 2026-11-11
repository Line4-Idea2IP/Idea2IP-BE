package com.line4.idea2ip.domain.material.controller;

import com.line4.idea2ip.domain.material.dto.MaterialCreateReq;
import com.line4.idea2ip.domain.material.dto.MaterialCreateRes;
import com.line4.idea2ip.domain.material.dto.MaterialDetailRes;
import com.line4.idea2ip.domain.material.dto.MaterialListRes;
import com.line4.idea2ip.domain.material.dto.MaterialFilesRes;
import com.line4.idea2ip.domain.material.service.CreativeMaterialService;
import com.line4.idea2ip.global.jwt.CustomUserDetails;
import com.line4.idea2ip.global.response.SuccessResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CreativeMaterialController {

    private final CreativeMaterialService materialService;

    @PostMapping(value = "/api/projects/{projectId}/materials", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SuccessResponse<MaterialCreateRes>> createMaterial(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody MaterialCreateReq req
    ) {
        MaterialCreateRes response = materialService.createMaterial(userDetails.getUserId(), projectId, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(SuccessResponse.created(response));
    }

    @PostMapping(value = "/api/materials/{materialId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuccessResponse<MaterialFilesRes>> addMaterialFiles(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("materialId") Long materialId,
            @RequestPart("files") List<MultipartFile> files
    ) {
        MaterialFilesRes response = materialService.addMaterialFiles(userDetails.getUserId(), materialId, files);
        return ResponseEntity.status(HttpStatus.CREATED).body(SuccessResponse.created(response));
    }

    @GetMapping("/api/projects/{projectId}/materials")
    public ResponseEntity<SuccessResponse<MaterialListRes>> getMaterials(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("projectId") Long projectId
    ) {
        MaterialListRes response = materialService.getMaterials(userDetails.getUserId(), projectId);
        return ResponseEntity.ok(SuccessResponse.from(response));
    }

    @GetMapping("/api/materials/{materialId}")
    public ResponseEntity<SuccessResponse<MaterialDetailRes>> getMaterialDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("materialId") Long materialId
    ) {
        MaterialDetailRes response = materialService.getMaterialDetail(userDetails.getUserId(), materialId);
        return ResponseEntity.ok(SuccessResponse.from(response));
    }

    @GetMapping("/api/materials/{materialId}/files/{fileId}")
    public ResponseEntity<byte[]> getMaterialImage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("materialId") Long materialId,
            @PathVariable("fileId") Long fileId
    ) {
        CreativeMaterialService.MaterialImage image =
                materialService.getMaterialImage(userDetails.getUserId(), materialId, fileId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(image.contentType())
                .body(image.content());
    }
}
