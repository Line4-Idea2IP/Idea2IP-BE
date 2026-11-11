package com.line4.idea2ip.domain.artwork.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ArtworkUpdateReq {

    @JsonSetter(nulls = Nulls.FAIL)
    @Pattern(regexp = "(?sU).*\\S.*", message = "최종 작품 제목을 입력해주세요.")
    private String title;

    private String description;

    @JsonIgnore
    private boolean descriptionPresent;

    @JsonSetter
    public void setDescription(String description) {
        this.description = description;
        this.descriptionPresent = true;
    }
}
