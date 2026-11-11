package com.line4.idea2ip.domain.project.service;

import com.line4.idea2ip.domain.interview.entity.AiInterview;
import com.line4.idea2ip.domain.interview.repository.AiInterviewRepository;
import com.line4.idea2ip.domain.interview.repository.AiInterviewRepository.ProjectInterviewStatus;
import com.line4.idea2ip.domain.material.repository.CreativeMaterialRepository;
import com.line4.idea2ip.domain.material.repository.CreativeMaterialRepository.ProjectMaterialCount;
import com.line4.idea2ip.domain.project.dto.ProjectCreateReq;
import com.line4.idea2ip.domain.project.dto.ProjectCreateRes;
import com.line4.idea2ip.domain.project.dto.ProjectDetailRes;
import com.line4.idea2ip.domain.project.dto.ProjectListRes;
import com.line4.idea2ip.domain.project.dto.ProjectThumbnailRes;
import com.line4.idea2ip.domain.project.entity.Project;
import com.line4.idea2ip.domain.project.repository.ProjectRepository;
import com.line4.idea2ip.domain.project.storage.ProjectThumbnailStorage;
import com.line4.idea2ip.domain.user.entity.User;
import com.line4.idea2ip.domain.report.ProjectReportReadService;
import com.line4.idea2ip.domain.report.ReportModels.ProjectReportStatus;
import com.line4.idea2ip.domain.report.ReportModels.ReportStatus;
import com.line4.idea2ip.domain.user.repository.UserRepository;
import com.line4.idea2ip.global.exception.BaseException;
import com.line4.idea2ip.global.response.code.ErrorResponseCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectThumbnailStorage thumbnailStorage;
    private final ProjectReportReadService reportRead;
    private final CreativeMaterialRepository materials;
    private final AiInterviewRepository interviews;

    @Transactional
    public ProjectCreateRes createProject(Long userId, ProjectCreateReq req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorResponseCode.UNAUTHORIZED_REQUEST));
        Project project = Project.builder()
                .user(user)
                .title(req.getTitle())
                .description(req.getDescription())
                .artworkType(req.getArtworkType())
                .build();

        Project savedProject = projectRepository.save(project);

        return ProjectCreateRes.builder()
                .projectId(savedProject.getProjectId())
                .title(savedProject.getTitle())
                .build();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProjectListRes getMyProjects(Long userId) {
        verifyUser(userId);

        List<Project> recentProjects = projectRepository.findAllByUser_UserIdOrderByUpdateAtDescProjectIdDesc(userId);
        var statuses = reportRead.statuses(recentProjects);
        var progress = getProgress(recentProjects.stream().map(Project::getProjectId).toList());
        List<ProjectListRes.ProjectItem> projects = recentProjects.stream().map(project -> {
            var status = statuses.getOrDefault(project.getProjectId(), ProjectReportStatus.empty());
            return ProjectListRes.ProjectItem.builder()
                        .projectId(project.getProjectId())
                        .title(project.getTitle())
                        .createdAt(project.getCreatedAt())
                        .updatedAt(project.getUpdateAt())
                        .artworkType(project.getArtworkType())
                        .thumbnailUrl(project.getThumbnailUrl())
                        .materialCount(progress.materialCount(project.getProjectId()))
                        .interviewStatus(progress.interviewStatus(project.getProjectId()))
                        .reportStatus(status.reportStatus())
                        .latestReadyVersionId(status.latestReadyVersionId())
                        .reportUpdateAvailable(status.reportUpdateAvailable())
                        .build();
        }).toList();
        long completed = projects.stream().filter(item -> item.getReportStatus() == ReportStatus.READY).count();

        return ProjectListRes.builder()
                .projects(projects)
                .summary(new ProjectListRes.Summary(projects.size(), completed, projects.size() - completed))
                .build();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProjectDetailRes getProjectDetail(Long userId, Long projectId) {
        Project project = getOwnedProject(userId, projectId);

        var status = reportRead.statuses(List.of(project)).getOrDefault(projectId, ProjectReportStatus.empty());
        var progress = getProgress(List.of(projectId));
        return ProjectDetailRes.builder()
                .materialCount(progress.materialCount(projectId))
                .interviewStatus(progress.interviewStatus(projectId))
                .reportStatus(status.reportStatus())
                .latestReadyVersionId(status.latestReadyVersionId())
                .reportUpdateAvailable(status.reportUpdateAvailable())
                .projectId(project.getProjectId())
                .title(project.getTitle())
                .description(project.getDescription())
                .createdAt(project.getCreatedAt())
                .artworkType(project.getArtworkType())
                .build();
    }

    @Transactional
    public ProjectThumbnailRes updateThumbnail(Long userId, Long projectId, MultipartFile thumbnail) {
        Project project = getOwnedProjectForUpdate(userId, projectId);

        String previousUrl = project.getThumbnailUrl();
        String thumbnailUrl = thumbnailStorage.store(thumbnail);
        project.updateThumbnail(thumbnailUrl);
        thumbnailStorage.deleteAfterCommit(previousUrl);

        return ProjectThumbnailRes.builder()
                .projectId(project.getProjectId())
                .thumbnailUrl(thumbnailUrl)
                .build();
    }

    @Transactional(readOnly = true)
    public Project getOwnedProject(Long userId, Long projectId) {
        verifyUser(userId);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BaseException(ErrorResponseCode.PROJECT_NOT_FOUND));
        verifyOwner(project, userId);
        return project;
    }

    public Project getOwnedProjectForUpdate(Long userId, Long projectId) {
        verifyUser(userId);
        Project project = projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new BaseException(ErrorResponseCode.PROJECT_NOT_FOUND));
        verifyOwner(project, userId);
        return project;
    }

    private ProjectProgress getProgress(List<Long> projectIds) {
        if (projectIds.isEmpty()) {
            return new ProjectProgress(Map.of(), Map.of());
        }
        var materialCounts = materials.countByProjects(projectIds).stream().collect(
                Collectors.toMap(ProjectMaterialCount::getProjectId, ProjectMaterialCount::getMaterialCount));
        var interviewStatuses = interviews.findProjectStatuses(projectIds).stream().collect(
                Collectors.toMap(ProjectInterviewStatus::getProjectId, ProjectInterviewStatus::getStatus));
        return new ProjectProgress(materialCounts, interviewStatuses);
    }

    private record ProjectProgress(Map<Long, Long> materialCounts, Map<Long, AiInterview.Status> interviewStatuses) {
        long materialCount(Long projectId) {
            return materialCounts.getOrDefault(projectId, 0L);
        }

        AiInterview.Status interviewStatus(Long projectId) {
            return interviewStatuses.getOrDefault(projectId, AiInterview.Status.NOT_STARTED);
        }
    }

    private void verifyUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BaseException(ErrorResponseCode.UNAUTHORIZED_REQUEST);
        }
    }

    public void verifyOwner(Project project, Long userId) {
        if (!project.getUser().getUserId().equals(userId)) {
            throw new BaseException(ErrorResponseCode.ACCESS_DENIED_REQUEST);
        }
    }
}
