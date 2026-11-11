package com.line4.idea2ip.domain.material.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.line4.idea2ip.domain.material.entity.MaterialType;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class MaterialUpdateReq {

    @JsonSetter(nulls = Nulls.FAIL)
    @Pattern(regexp = "(?sU).*\\S.*", message = "자료 제목을 입력해주세요.")
    @Size(max = MaterialCreateReq.TITLE_MAX, message = "자료 제목은 200자 이하여야 합니다.")
    private String title;

    @JsonSetter(nulls = Nulls.FAIL)
    private MaterialType materialType;

    @Size(max = MaterialCreateReq.DESCRIPTION_MAX, message = "자료 설명은 5000자 이하여야 합니다.")
    private String description;

    @Size(max = 255, message = "AI 도구 이름은 255자 이하여야 합니다.")
    private String usedAi;

    private LocalDate productionDate;

    @JsonIgnore
    private boolean descriptionPresent;

    @JsonIgnore
    private boolean usedAiPresent;

    @JsonIgnore
    private boolean productionDatePresent;

    @JsonSetter
    public void setDescription(String description) {
        this.description = description;
        this.descriptionPresent = true;
    }

    @JsonSetter
    public void setUsedAi(String usedAi) {
        this.usedAi = usedAi;
        this.usedAiPresent = true;
    }

    @JsonSetter
    public void setProductionDate(LocalDate productionDate) {
        this.productionDate = productionDate;
        this.productionDatePresent = true;
    }
}
