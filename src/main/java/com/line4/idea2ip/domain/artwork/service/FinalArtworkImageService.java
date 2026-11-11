package com.line4.idea2ip.domain.artwork.service;

import com.line4.idea2ip.domain.artwork.dto.ArtworkImageRes;
import com.line4.idea2ip.domain.artwork.entity.FinalArtwork;
import com.line4.idea2ip.domain.artwork.repository.FinalArtworkRepository;
import com.line4.idea2ip.domain.project.service.ProjectService;
import com.line4.idea2ip.global.exception.BaseException;
import com.line4.idea2ip.global.response.code.ErrorResponseCode;
import com.line4.idea2ip.global.storage.LocalImageStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.NoSuchFileException;

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
        deleteImageAfterCommit(previousPath);
        return new ArtworkImageRes(artwork.getArtworkId(), projectId, originalFileName);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteImageAfterCommit(String imagePath) {
        if (imagePath != null) {
            imageStorage.deleteAfterCommit(imagePath);
        }
    }

    @Transactional(readOnly = true)
    public ArtworkImage getImage(Long userId, Long projectId) {
        projectService.getOwnedProject(userId, projectId);
        FinalArtwork artwork = artworkRepository.findByProject_ProjectId(projectId)
                .orElseThrow(() -> new BaseException(ErrorResponseCode.ARTWORK_NOT_FOUND));
        String imagePath = artwork.getImagePath();
        if (imagePath == null) {
            throw new BaseException(ErrorResponseCode.ARTWORK_IMAGE_NOT_FOUND);
        }

        try {
            byte[] content = imageStorage.read(imagePath);
            MediaType contentType = imagePath.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
            return new ArtworkImage(contentType, content);
        } catch (NoSuchFileException e) {
            throw new BaseException(ErrorResponseCode.ARTWORK_IMAGE_NOT_FOUND);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read artwork image " + artwork.getArtworkId(), e);
        }
    }

    public record ArtworkImage(MediaType contentType, byte[] content) {
    }
}
