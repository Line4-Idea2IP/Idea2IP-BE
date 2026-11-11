package com.line4.idea2ip.domain.artwork.controller;

import com.line4.idea2ip.domain.artwork.dto.ArtworkCreateReq;
import com.line4.idea2ip.domain.artwork.dto.ArtworkCreateRes;
import com.line4.idea2ip.domain.artwork.dto.ArtworkDetailRes;
import com.line4.idea2ip.domain.artwork.dto.ArtworkImageRes;
import com.line4.idea2ip.domain.artwork.dto.ArtworkUpdateReq;
import com.line4.idea2ip.domain.artwork.dto.ArtworkUpdateRes;
import com.line4.idea2ip.domain.artwork.service.FinalArtworkImageService;
import com.line4.idea2ip.domain.artwork.service.FinalArtworkService;
import com.line4.idea2ip.global.jwt.CustomUserDetails;
import com.line4.idea2ip.global.response.SuccessResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class FinalArtworkController {

    private final FinalArtworkService artworkService;
    private final FinalArtworkImageService artworkImageService;

    @PostMapping(value = "/{projectId}/artwork", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SuccessResponse<ArtworkCreateRes>> createArtwork(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody ArtworkCreateReq req
    ) {
        ArtworkCreateRes response = artworkService.createArtwork(userDetails.getUserId(), projectId, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(SuccessResponse.created(response));
    }

    @GetMapping("/{projectId}/artwork")
    public ResponseEntity<SuccessResponse<ArtworkDetailRes>> getArtwork(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("projectId") Long projectId
    ) {
        ArtworkDetailRes response = artworkService.getArtwork(userDetails.getUserId(), projectId);
        return ResponseEntity.ok(SuccessResponse.from(response));
    }

    @PatchMapping(value = "/{projectId}/artwork", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SuccessResponse<ArtworkUpdateRes>> updateArtwork(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody ArtworkUpdateReq req
    ) {
        ArtworkUpdateRes response = artworkService.updateArtwork(userDetails.getUserId(), projectId, req);
        return ResponseEntity.ok(SuccessResponse.from(response));
    }

    @DeleteMapping("/{projectId}/artwork")
    public ResponseEntity<Void> deleteArtwork(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("projectId") Long projectId
    ) {
        artworkService.deleteArtwork(userDetails.getUserId(), projectId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping(value = "/{projectId}/artwork/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuccessResponse<ArtworkImageRes>> updateImage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("projectId") Long projectId,
            @RequestPart("file") MultipartFile file
    ) {
        ArtworkImageRes response = artworkImageService.updateImage(userDetails.getUserId(), projectId, file);
        return ResponseEntity.ok(SuccessResponse.from(response));
    }

    @GetMapping("/{projectId}/artwork/image")
    public ResponseEntity<byte[]> getImage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("projectId") Long projectId
    ) {
        FinalArtworkImageService.ArtworkImage image = artworkImageService.getImage(userDetails.getUserId(), projectId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(image.contentType())
                .body(image.content());
    }
}
