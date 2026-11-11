package com.line4.idea2ip.domain.artwork.dto;

import jakarta.validation.constraints.NotBlank;

public record ArtworkCreateReq(
        @NotBlank(message = "최종 작품 제목을 입력해주세요.") String title,
        String description
) {
}
