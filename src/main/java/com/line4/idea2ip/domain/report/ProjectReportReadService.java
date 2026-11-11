package com.line4.idea2ip.domain.report;

import com.line4.idea2ip.domain.project.entity.Project;
import com.line4.idea2ip.domain.project.service.ProjectInputChanges;
import com.line4.idea2ip.domain.report.ReportModels.ProjectReportStatus;
import com.line4.idea2ip.domain.report.ReportModels.ReportStatus;
import com.line4.idea2ip.global.job.PersistentJob.Status;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectReportReadService {
    private final ReportRepository reports;
    private final ReportVersionRepository versions;
    private final ProjectInputChanges changes;

    public Map<Long, ProjectReportStatus> statuses(List<Project> projects) {
        if (projects.isEmpty()) return Map.of();
        var byId = projects.stream().collect(Collectors.toMap(Project::getProjectId, project -> project));
        var found = reports.findByProject_ProjectIdIn(List.copyOf(byId.keySet()));
        if (found.isEmpty()) return Map.of();
        var reportIds = found.stream().map(Report::getId).toList();
        var activeIds = Set.copyOf(versions.reportIdsWithStatuses(reportIds, List.of(Status.QUEUED, Status.RUNNING)));
        var failedIds = Set.copyOf(versions.reportIdsWithStatuses(reportIds, List.of(Status.FAILED)));
        var result = new HashMap<Long, ProjectReportStatus>();
        for (var report : found) {
            Long projectId = report.getProject().getProjectId();
            var ready = report.getLatestReadyVersion();
            boolean updateAvailable = ready != null && byId.get(projectId).getInputRevision() > ready.getInputRevision()
                    && changes.summarize(projectId, ready.getInputRevision(), byId.get(projectId).getInputRevision()).hasChanges();
            result.put(projectId, new ProjectReportStatus(ready != null ? ReportStatus.READY
                    : activeIds.contains(report.getId()) ? ReportStatus.GENERATING
                    : failedIds.contains(report.getId()) ? ReportStatus.FAILED : ReportStatus.NOT_STARTED,
                    ready == null ? null : ready.getId(), updateAvailable));
        }
        return result;
    }
}
