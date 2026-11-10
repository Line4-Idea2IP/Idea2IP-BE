package com.line4.idea2ip.domain.project.dto;

import com.line4.idea2ip.domain.project.entity.ArtworkType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ProjectCreateReq {

    @NotBlank(message = "프로젝트 제목을 입력해주세요.")
    private String title;

    private String description;

    @NotNull(message = "작품 유형을 선택해주세요.")
    private ArtworkType artworkType;
}
