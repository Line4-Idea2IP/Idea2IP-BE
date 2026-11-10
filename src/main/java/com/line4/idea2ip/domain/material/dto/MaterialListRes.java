package com.line4.idea2ip.domain.material.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record MaterialListRes(List<MaterialItem> materials) {

    public record MaterialItem(
            Long materialId,
            int materialOrder,
            String title,
            String description,
            LocalDate productionDate,
            LocalDateTime createdAt,
            List<MaterialFileRes> files
    ) {
    }
}
