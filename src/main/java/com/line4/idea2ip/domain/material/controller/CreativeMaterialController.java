package com.line4.idea2ip.domain.material.controller;

import com.line4.idea2ip.domain.material.dto.MaterialDetailRes;
import com.line4.idea2ip.domain.material.dto.MaterialFilesRes;
import com.line4.idea2ip.domain.material.dto.MaterialUpdateReq;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/materials")
@RequiredArgsConstructor
public class CreativeMaterialController {

    private final CreativeMaterialService materialService;

    @PostMapping(value = "/{materialId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuccessResponse<MaterialFilesRes>> addMaterialFiles(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("materialId") Long materialId,
            @RequestPart("files") List<MultipartFile> files
    ) {
        MaterialFilesRes response = materialService.addMaterialFiles(userDetails.getUserId(), materialId, files);
        return ResponseEntity.status(HttpStatus.CREATED).body(SuccessResponse.created(response));
    }

    @PatchMapping(value = "/{materialId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuccessResponse<MaterialFilesRes>> updateMaterialFiles(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("materialId") Long materialId,
            @RequestPart(value = "newFiles", required = false) List<MultipartFile> newFiles,
            @RequestParam(value = "deleteFileIds", required = false) List<Long> deleteFileIds
    ) {
        MaterialFilesRes response = materialService.updateMaterialFiles(
                userDetails.getUserId(), materialId, newFiles, deleteFileIds);
        return ResponseEntity.ok(SuccessResponse.from(response));
    }

    @GetMapping("/{materialId}")
    public ResponseEntity<SuccessResponse<MaterialDetailRes>> getMaterialDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("materialId") Long materialId
    ) {
        MaterialDetailRes response = materialService.getMaterialDetail(userDetails.getUserId(), materialId);
        return ResponseEntity.ok(SuccessResponse.from(response));
    }

    @PatchMapping(value = "/{materialId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SuccessResponse<MaterialDetailRes>> updateMaterial(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("materialId") Long materialId,
            @Valid @RequestBody MaterialUpdateReq req
    ) {
        MaterialDetailRes response = materialService.updateMaterial(userDetails.getUserId(), materialId, req);
        return ResponseEntity.ok(SuccessResponse.from(response));
    }

    @GetMapping("/{materialId}/files/{fileId}")
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
