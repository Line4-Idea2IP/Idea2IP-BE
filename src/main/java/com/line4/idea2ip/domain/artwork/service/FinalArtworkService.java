package com.line4.idea2ip.domain.artwork.service;

import com.line4.idea2ip.domain.artwork.dto.ArtworkCreateReq;
import com.line4.idea2ip.domain.artwork.dto.ArtworkCreateRes;
import com.line4.idea2ip.domain.artwork.dto.ArtworkDetailRes;
import com.line4.idea2ip.domain.artwork.dto.ArtworkUpdateReq;
import com.line4.idea2ip.domain.artwork.dto.ArtworkUpdateRes;
import com.line4.idea2ip.domain.artwork.entity.FinalArtwork;
import com.line4.idea2ip.domain.artwork.repository.FinalArtworkRepository;
import com.line4.idea2ip.domain.project.entity.Project;
import com.line4.idea2ip.domain.project.service.ProjectService;
import com.line4.idea2ip.domain.project.service.ProjectInputChanges;
import com.line4.idea2ip.domain.project.entity.ProjectInputChange.Operation;
import com.line4.idea2ip.domain.project.entity.ProjectInputChange.Type;
import java.util.Objects;
import com.line4.idea2ip.global.exception.BaseException;
import com.line4.idea2ip.global.response.code.ErrorResponseCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FinalArtworkService {

    private final ProjectService projectService;
    private final ProjectInputChanges inputChanges;
    private final FinalArtworkRepository artworkRepository;
    private final FinalArtworkImageService artworkImageService;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ArtworkCreateRes createArtwork(Long userId, Long projectId, ArtworkCreateReq req) {
        Project project = projectService.getOwnedProjectForUpdate(userId, projectId);
        if (artworkRepository.existsByProject_ProjectId(projectId)) {
            throw new BaseException(ErrorResponseCode.ARTWORK_ALREADY_EXISTS);
        }

        FinalArtwork artwork = new FinalArtwork(project, req.title(), req.description());
        FinalArtwork savedArtwork = artworkRepository.save(artwork);
        inputChanges.record(project, Type.FINAL_ARTWORK, Operation.ADDED, savedArtwork.getArtworkId());
        return new ArtworkCreateRes(savedArtwork.getArtworkId(), projectId, savedArtwork.getTitle());
    }

    @Transactional(readOnly = true)
    public ArtworkDetailRes getArtwork(Long userId, Long projectId) {
        projectService.getOwnedProject(userId, projectId);
        FinalArtwork artwork = artworkRepository.findByProject_ProjectId(projectId)
                .orElseThrow(() -> new BaseException(ErrorResponseCode.ARTWORK_NOT_FOUND));
        String fileUrl = artwork.getImagePath() == null
                ? null : "/api/projects/" + projectId + "/artwork/image";
        return new ArtworkDetailRes(
                artwork.getArtworkId(),
                projectId,
                artwork.getTitle(),
                artwork.getDescription(),
                artwork.getOriginalFileName(),
                fileUrl
        );
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ArtworkUpdateRes updateArtwork(Long userId, Long projectId, ArtworkUpdateReq req) {
        Project project = projectService.getOwnedProjectForUpdate(userId, projectId);
        FinalArtwork artwork = artworkRepository.findByProject_ProjectId(projectId)
                .orElseThrow(() -> new BaseException(ErrorResponseCode.ARTWORK_NOT_FOUND));
        String title = req.getTitle() != null ? req.getTitle() : artwork.getTitle();
        String description = req.isDescriptionPresent() ? req.getDescription() : artwork.getDescription();
        if (!Objects.equals(title, artwork.getTitle()) || !Objects.equals(description, artwork.getDescription())) {
            artwork.updateInfo(title, description);
            inputChanges.record(project, Type.FINAL_ARTWORK, Operation.UPDATED, artwork.getArtworkId());
        }
        return new ArtworkUpdateRes(
                artwork.getArtworkId(),
                artwork.getTitle(),
                artwork.getDescription(),
                artwork.getOriginalFileName()
        );
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteArtwork(Long userId, Long projectId) {
        Project project = projectService.getOwnedProjectForUpdate(userId, projectId);
        FinalArtwork artwork = artworkRepository.findByProject_ProjectId(projectId)
                .orElseThrow(() -> new BaseException(ErrorResponseCode.ARTWORK_NOT_FOUND));
        artworkImageService.deleteImageAfterCommit(artwork.getImagePath());
        artworkRepository.delete(artwork);
        inputChanges.record(project, Type.FINAL_ARTWORK, Operation.DELETED, artwork.getArtworkId());
    }
}
