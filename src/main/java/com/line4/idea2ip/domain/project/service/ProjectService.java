package com.line4.idea2ip.domain.project.service;

import com.line4.idea2ip.domain.project.dto.ProjectCreateReq;
import com.line4.idea2ip.domain.project.dto.ProjectCreateRes;
import com.line4.idea2ip.domain.project.dto.ProjectDetailRes;
import com.line4.idea2ip.domain.project.dto.ProjectListRes;
import com.line4.idea2ip.domain.project.dto.ProjectThumbnailRes;
import com.line4.idea2ip.domain.project.entity.Project;
import com.line4.idea2ip.domain.project.repository.ProjectRepository;
import com.line4.idea2ip.domain.project.storage.ProjectThumbnailStorage;
import com.line4.idea2ip.domain.user.entity.User;
import com.line4.idea2ip.domain.user.repository.UserRepository;
import com.line4.idea2ip.global.exception.BaseException;
import com.line4.idea2ip.global.response.code.ErrorResponseCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectThumbnailStorage thumbnailStorage;

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

    @Transactional(readOnly = true)
    public ProjectListRes getMyProjects(Long userId) {
        verifyUser(userId);

        List<Project> recentProjects = projectRepository.findAllByUser_UserIdOrderByUpdateAtDescProjectIdDesc(userId);
        List<ProjectListRes.ProjectItem> projects = recentProjects.stream()
                .map(project -> ProjectListRes.ProjectItem.builder()
                        .projectId(project.getProjectId())
                        .title(project.getTitle())
                        .createdAt(project.getCreatedAt())
                        .updatedAt(project.getUpdateAt())
                        .artworkType(project.getArtworkType())
                        .thumbnailUrl(project.getThumbnailUrl())
                        .build())
                .toList();

        return ProjectListRes.builder()
                .projects(projects)
                .build();
    }

    @Transactional(readOnly = true)
    public ProjectDetailRes getProjectDetail(Long userId, Long projectId) {
        verifyUser(userId);

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BaseException(ErrorResponseCode.PROJECT_NOT_FOUND));

        verifyOwner(project, userId);

        return ProjectDetailRes.builder()
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

    public Project getOwnedProjectForUpdate(Long userId, Long projectId) {
        verifyUser(userId);
        Project project = projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new BaseException(ErrorResponseCode.PROJECT_NOT_FOUND));
        verifyOwner(project, userId);
        return project;
    }

    private void verifyUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BaseException(ErrorResponseCode.UNAUTHORIZED_REQUEST);
        }
    }

    private void verifyOwner(Project project, Long userId) {
        if (!project.getUser().getUserId().equals(userId)) {
            throw new BaseException(ErrorResponseCode.ACCESS_DENIED_REQUEST);
        }
    }
}
