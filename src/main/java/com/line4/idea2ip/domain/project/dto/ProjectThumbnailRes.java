package com.line4.idea2ip.domain.project.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProjectThumbnailRes {
    private Long projectId;
    private String thumbnailUrl;
}
