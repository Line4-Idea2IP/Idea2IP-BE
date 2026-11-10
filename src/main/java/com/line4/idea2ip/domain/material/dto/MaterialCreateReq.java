package com.line4.idea2ip.domain.material.dto;

import com.line4.idea2ip.domain.material.entity.MaterialType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record MaterialCreateReq(
        @NotNull(message = "자료 유형을 선택해주세요.") MaterialType materialType,
        @NotBlank(message = "자료 제목을 입력해주세요.") String title,
        String description,
        @Size(max = 255, message = "AI 도구 이름은 255자 이하여야 합니다.") String usedAi,
        LocalDate productionDate
) {
}
