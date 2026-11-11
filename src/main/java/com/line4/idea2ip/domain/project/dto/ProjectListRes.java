package com.line4.idea2ip.domain.project.dto;

import com.line4.idea2ip.domain.project.entity.ArtworkType;
import com.line4.idea2ip.domain.report.ReportModels.ReportStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ProjectListRes {
    private List<ProjectItem> projects;
    private Summary summary;

    public record Summary(long totalProjects, long completedProjects, long inProgressProjects) {}

    @Getter
    @Builder
    public static class ProjectItem {
        private Long projectId;
        private String title;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private ArtworkType artworkType;
        private String thumbnailUrl;
        private ReportStatus reportStatus;
        private Long latestReadyVersionId;
        private boolean reportUpdateAvailable;
    }
}
