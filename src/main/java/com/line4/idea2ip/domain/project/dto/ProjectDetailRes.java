package com.line4.idea2ip.domain.project.dto;

import com.line4.idea2ip.domain.project.entity.ArtworkType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ProjectDetailRes {
    private Long projectId;
    private String title;
    private String description;
    private LocalDateTime createdAt;
    private ArtworkType artworkType;
}
