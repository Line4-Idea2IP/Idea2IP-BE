package com.line4.idea2ip.domain.artwork.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.line4.idea2ip.domain.material.dto.MaterialCreateReq;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ArtworkUpdateReq {

    @JsonSetter(nulls = Nulls.FAIL)
    @Pattern(regexp = "(?sU).*\\S.*", message = "최종 작품 제목을 입력해주세요.")
    @Size(max = MaterialCreateReq.TITLE_MAX, message = "최종 작품 제목은 200자 이하여야 합니다.")
    private String title;

    @Size(max = MaterialCreateReq.DESCRIPTION_MAX, message = "최종 작품 설명은 5000자 이하여야 합니다.")
    private String description;

    @JsonIgnore
    private boolean descriptionPresent;

    @JsonSetter
    public void setDescription(String description) {
        this.description = description;
        this.descriptionPresent = true;
    }
}
