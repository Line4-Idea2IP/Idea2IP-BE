package com.line4.idea2ip.domain.material.dto;

import java.util.List;

public record MaterialFilesRes(Long materialId, List<MaterialFileRes> files) {
}
