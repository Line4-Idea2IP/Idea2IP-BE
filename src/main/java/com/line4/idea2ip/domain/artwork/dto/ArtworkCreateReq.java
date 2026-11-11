package com.line4.idea2ip.domain.artwork.dto;

import com.line4.idea2ip.domain.material.dto.MaterialCreateReq;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ArtworkCreateReq(
        @NotBlank(message = "최종 작품 제목을 입력해주세요.")
        @Size(max = MaterialCreateReq.TITLE_MAX, message = "최종 작품 제목은 200자 이하여야 합니다.") String title,
        @Size(max = MaterialCreateReq.DESCRIPTION_MAX, message = "최종 작품 설명은 5000자 이하여야 합니다.") String description
) {
}
