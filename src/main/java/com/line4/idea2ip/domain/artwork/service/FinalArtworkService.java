package com.line4.idea2ip.domain.artwork.service;

import com.line4.idea2ip.domain.artwork.dto.ArtworkCreateReq;
import com.line4.idea2ip.domain.artwork.dto.ArtworkCreateRes;
import com.line4.idea2ip.domain.artwork.entity.FinalArtwork;
import com.line4.idea2ip.domain.artwork.repository.FinalArtworkRepository;
import com.line4.idea2ip.domain.project.entity.Project;
import com.line4.idea2ip.domain.project.service.ProjectService;
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
    private final FinalArtworkRepository artworkRepository;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ArtworkCreateRes createArtwork(Long userId, Long projectId, ArtworkCreateReq req) {
        Project project = projectService.getOwnedProjectForUpdate(userId, projectId);
        if (artworkRepository.existsByProject_ProjectId(projectId)) {
            throw new BaseException(ErrorResponseCode.ARTWORK_ALREADY_EXISTS);
        }

        FinalArtwork artwork = new FinalArtwork(project, req.title(), req.description());
        FinalArtwork savedArtwork = artworkRepository.save(artwork);
        return new ArtworkCreateRes(savedArtwork.getArtworkId(), projectId, savedArtwork.getTitle());
    }
}
