package com.line4.idea2ip.domain.artwork.dto;

public record ArtworkDetailRes(
        Long artworkId,
        Long projectId,
        String title,
        String description,
        String fileName,
        String fileUrl
) {
}
