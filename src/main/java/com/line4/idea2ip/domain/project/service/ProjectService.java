package com.line4.idea2ip.domain.project.service;

import com.line4.idea2ip.domain.project.dto.ProjectCreateReq;
import com.line4.idea2ip.domain.project.dto.ProjectCreateRes;
import com.line4.idea2ip.domain.project.dto.ProjectListRes;
import com.line4.idea2ip.domain.project.entity.Project;
import com.line4.idea2ip.domain.project.repository.ProjectRepository;
import com.line4.idea2ip.domain.user.entity.User;
import com.line4.idea2ip.domain.user.repository.UserRepository;
import com.line4.idea2ip.global.exception.BaseException;
import com.line4.idea2ip.global.response.code.ErrorResponseCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    @Transactional
    public ProjectCreateRes createProject(Long userId, ProjectCreateReq req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorResponseCode.UNAUTHORIZED_REQUEST));

        Project project = Project.builder()
                .user(user)
                .title(req.getTitle())
                .description(req.getDescription())
                .build();

        Project savedProject = projectRepository.save(project);

        return ProjectCreateRes.builder()
                .projectId(savedProject.getProjectId())
                .title(savedProject.getTitle())
                .build();
    }

    @Transactional(readOnly = true)
    public ProjectListRes getMyProjects(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BaseException(ErrorResponseCode.UNAUTHORIZED_REQUEST);
        }

        List<ProjectListRes.ProjectItem> projects = projectRepository.findAllByUser_UserId(userId).stream()
                .map(project -> ProjectListRes.ProjectItem.builder()
                        .projectId(project.getProjectId())
                        .title(project.getTitle())
                        .createdAt(project.getCreatedAt())
                        .build())
                .toList();

        return ProjectListRes.builder()
                .projects(projects)
                .build();
    }
}
