package com.line4.idea2ip.domain.material.dto;

import com.line4.idea2ip.domain.material.entity.MaterialType;

import java.time.LocalDate;
import java.util.List;

public record MaterialDetailRes(
        Long materialId,
        MaterialType materialType,
        String title,
        String description,
        String usedAi,
        LocalDate productionDate,
        List<MaterialFileRes> files
) {
}
