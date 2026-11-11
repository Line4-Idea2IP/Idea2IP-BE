package com.line4.idea2ip.domain.project.dto;

import com.line4.idea2ip.domain.interview.entity.AiInterview;
import com.line4.idea2ip.domain.project.entity.ArtworkType;
import com.line4.idea2ip.domain.report.ReportModels.ReportStatus;
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
    private long materialCount;
    private AiInterview.Status interviewStatus;
    private ReportStatus reportStatus;
    private Long latestReadyVersionId;
    private boolean reportUpdateAvailable;
}
