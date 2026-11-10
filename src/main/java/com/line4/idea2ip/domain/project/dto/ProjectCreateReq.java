package com.line4.idea2ip.domain.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ProjectCreateReq {

    @NotBlank(message = "프로젝트 제목을 입력해주세요.")
    @Size(max = 255, message = "프로젝트 제목은 255자 이하여야 합니다.")
    private String title;

    private String description;
}
