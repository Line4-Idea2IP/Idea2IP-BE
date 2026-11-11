package com.line4.idea2ip.domain.artwork.service;

import com.line4.idea2ip.domain.artwork.dto.ArtworkImageRes;
import com.line4.idea2ip.domain.artwork.entity.FinalArtwork;
import com.line4.idea2ip.domain.artwork.repository.FinalArtworkRepository;
import com.line4.idea2ip.domain.project.service.ProjectService;
import com.line4.idea2ip.global.exception.BaseException;
import com.line4.idea2ip.global.response.code.ErrorResponseCode;
import com.line4.idea2ip.global.storage.LocalImageStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FinalArtworkImageService {

    private final ProjectService projectService;
    private final FinalArtworkRepository artworkRepository;
    private final LocalImageStorage imageStorage;

    public FinalArtworkImageService(ProjectService projectService, FinalArtworkRepository artworkRepository,
                                    @Value("${idea2ip.artwork-image-dir}") String directory) {
        this.projectService = projectService;
        this.artworkRepository = artworkRepository;
        this.imageStorage = new LocalImageStorage(directory, "artwork-", ErrorResponseCode.INVALID_ARTWORK_IMAGE);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ArtworkImageRes updateImage(Long userId, Long projectId, MultipartFile file) {
        projectService.getOwnedProjectForUpdate(userId, projectId);
        FinalArtwork artwork = artworkRepository.findByProject_ProjectId(projectId)
                .orElseThrow(() -> new BaseException(ErrorResponseCode.ARTWORK_NOT_FOUND));

        String originalFileName = file.getOriginalFilename();
        if (originalFileName != null) {
            originalFileName = StringUtils.getFilename(originalFileName.replace('\\', '/'));
        }
        if (!StringUtils.hasText(originalFileName) || originalFileName.length() > 255) {
            throw new BaseException(ErrorResponseCode.INVALID_HTTP_MESSAGE_BODY);
        }

        String previousPath = artwork.getImagePath();
        LocalImageStorage.StoredImage image = imageStorage.store(file);
        artwork.updateImage(image.fileName(), originalFileName);
        if (previousPath != null) {
            imageStorage.deleteAfterCommit(previousPath);
        }
        return new ArtworkImageRes(artwork.getArtworkId(), projectId, originalFileName);
    }
}
