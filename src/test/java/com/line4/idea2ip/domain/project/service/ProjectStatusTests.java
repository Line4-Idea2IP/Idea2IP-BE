package com.line4.idea2ip.domain.project.service;

import com.line4.idea2ip.domain.interview.repository.AiInterviewRepository;
import com.line4.idea2ip.domain.material.repository.CreativeMaterialRepository;
import com.line4.idea2ip.domain.project.dto.ProjectListRes;
import com.line4.idea2ip.domain.project.entity.Project;
import com.line4.idea2ip.domain.project.entity.ProjectStatus;
import com.line4.idea2ip.domain.project.repository.ProjectRepository;
import com.line4.idea2ip.domain.project.storage.ProjectThumbnailStorage;
import com.line4.idea2ip.domain.report.ProjectReportReadService;
import com.line4.idea2ip.domain.report.ReportModels.ProjectReportStatus;
import com.line4.idea2ip.domain.report.ReportModels.ReportStatus;
import com.line4.idea2ip.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class ProjectStatusTests {

    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ProjectThumbnailStorage thumbnails = mock(ProjectThumbnailStorage.class);
    private final ProjectReportReadService reports = mock(ProjectReportReadService.class);
    private final CreativeMaterialRepository materials = mock(CreativeMaterialRepository.class);
    private final AiInterviewRepository interviews = mock(AiInterviewRepository.class);
    private final ProjectService service = new ProjectService(projects, users, thumbnails, reports, materials, interviews);

    @Test
    void statusAndSummaryUseCompletedReportsAndPreserveExistingProgressFields() {
        List<Project> found = List.of(project(1L), project(2L), project(3L),
                project(4L), project(5L), project(6L));
        List<Long> projectIds = List.of(1L, 2L, 3L, 4L, 5L, 6L);
        when(users.existsById(10L)).thenReturn(true);
        when(projects.findAllByUser_UserIdOrderByUpdateAtDescProjectIdDesc(10L)).thenReturn(found);
        when(reports.statuses(found)).thenReturn(Map.of(
                2L, ProjectReportStatus.empty(),
                3L, new ProjectReportStatus(ReportStatus.GENERATING, null, false),
                4L, new ProjectReportStatus(ReportStatus.FAILED, null, false),
                5L, new ProjectReportStatus(ReportStatus.READY, 50L, false),
                6L, new ProjectReportStatus(ReportStatus.READY, 60L, true)
        ));
        when(materials.countByProjects(projectIds)).thenReturn(List.of());
        when(interviews.findProjectStatuses(projectIds)).thenReturn(List.of());

        ProjectListRes response = service.getMyProjects(10L);

        assertEquals(projectIds, response.getProjects().stream().map(ProjectListRes.ProjectItem::getProjectId).toList());
        assertEquals(List.of(ProjectStatus.IN_PROGRESS, ProjectStatus.IN_PROGRESS,
                        ProjectStatus.IN_PROGRESS, ProjectStatus.IN_PROGRESS, ProjectStatus.COMPLETED, ProjectStatus.COMPLETED),
                response.getProjects().stream().map(ProjectListRes.ProjectItem::getStatus).toList());
        assertEquals(new ProjectListRes.Summary(6, 2, 4), response.getSummary());
        assertEquals(ReportStatus.GENERATING, response.getProjects().get(2).getReportStatus());
        assertEquals(ReportStatus.FAILED, response.getProjects().get(3).getReportStatus());
        assertEquals(60L, response.getProjects().get(5).getLatestReadyVersionId());
        assertTrue(response.getProjects().get(5).isReportUpdateAvailable());
        verify(reports).statuses(found);
        verify(materials).countByProjects(projectIds);
        verify(interviews).findProjectStatuses(projectIds);
    }

    @Test
    void emptyProjectListHasZeroSummaryAndSkipsProgressQueries() {
        when(users.existsById(10L)).thenReturn(true);
        when(projects.findAllByUser_UserIdOrderByUpdateAtDescProjectIdDesc(10L)).thenReturn(List.of());
        when(reports.statuses(List.of())).thenReturn(Map.of());

        ProjectListRes response = service.getMyProjects(10L);

        assertTrue(response.getProjects().isEmpty());
        assertEquals(new ProjectListRes.Summary(0, 0, 0), response.getSummary());
        verifyNoInteractions(materials, interviews);
    }

    private Project project(Long projectId) {
        Project project = mock(Project.class);
        when(project.getProjectId()).thenReturn(projectId);
        return project;
    }
}
