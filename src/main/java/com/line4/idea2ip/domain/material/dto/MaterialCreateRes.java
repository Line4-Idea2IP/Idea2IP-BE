package com.line4.idea2ip.domain.material.dto;

import java.util.List;

public record MaterialCreateRes(Long materialId, Long projectId, String title, List<FileItem> files) {

    public record FileItem(Long fileId, String fileName) {
    }
}
