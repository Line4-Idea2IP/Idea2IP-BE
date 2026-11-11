package com.line4.idea2ip.domain.artwork.controller;

import com.line4.idea2ip.domain.artwork.dto.ArtworkCreateReq;
import com.line4.idea2ip.domain.artwork.dto.ArtworkCreateRes;
import com.line4.idea2ip.domain.artwork.service.FinalArtworkService;
import com.line4.idea2ip.global.jwt.CustomUserDetails;
import com.line4.idea2ip.global.response.SuccessResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class FinalArtworkController {

    private final FinalArtworkService artworkService;

    @PostMapping(value = "/{projectId}/artwork", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SuccessResponse<ArtworkCreateRes>> createArtwork(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody ArtworkCreateReq req
    ) {
        ArtworkCreateRes response = artworkService.createArtwork(userDetails.getUserId(), projectId, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(SuccessResponse.created(response));
    }
}
