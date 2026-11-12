package com.line4.idea2ip.domain.material.dto;

import com.line4.idea2ip.domain.material.entity.MaterialType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record MaterialCreateReq(
        @NotNull(message = "자료 유형을 선택해주세요.") MaterialType materialType,
        @NotBlank(message = "자료 제목을 입력해주세요.")
        @Size(max = MaterialCreateReq.TITLE_MAX, message = "자료 제목은 200자 이하여야 합니다.") String title,
        @Size(max = MaterialCreateReq.DESCRIPTION_MAX, message = "자료 설명은 5000자 이하여야 합니다.") String description,
        @Size(max = 255, message = "AI 도구 이름은 255자 이하여야 합니다.") String usedAi,
        LocalDate productionDate
) {
    // Keeps each AI source (title, type, description and tool) under the 8000-character AI input limit.
    public static final int TITLE_MAX = 200;
    public static final int DESCRIPTION_MAX = 5000;
}
