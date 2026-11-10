package com.line4.idea2ip.domain.project.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ProjectListRes {
    private List<ProjectItem> projects;

    @Getter
    @Builder
    public static class ProjectItem {
        private Long projectId;
        private String title;
        private LocalDateTime createdAt;
    }
}
