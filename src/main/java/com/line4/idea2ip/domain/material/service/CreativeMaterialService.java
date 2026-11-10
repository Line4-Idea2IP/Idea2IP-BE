package com.line4.idea2ip.domain.material.service;

import com.line4.idea2ip.domain.material.dto.MaterialCreateReq;
import com.line4.idea2ip.domain.material.dto.MaterialCreateRes;
import com.line4.idea2ip.domain.material.entity.CreativeMaterial;
import com.line4.idea2ip.domain.material.entity.CreativeMaterialFile;
import com.line4.idea2ip.domain.material.entity.MaterialType;
import com.line4.idea2ip.domain.material.repository.CreativeMaterialFileRepository;
import com.line4.idea2ip.domain.material.repository.CreativeMaterialRepository;
import com.line4.idea2ip.domain.project.entity.Project;
import com.line4.idea2ip.domain.project.service.ProjectService;
import com.line4.idea2ip.global.exception.BaseException;
import com.line4.idea2ip.global.response.code.ErrorResponseCode;
import com.line4.idea2ip.global.storage.LocalImageStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.List;

@Service
public class CreativeMaterialService {

    private final ProjectService projectService;
    private final CreativeMaterialRepository materialRepository;
    private final CreativeMaterialFileRepository fileRepository;
    private final LocalImageStorage imageStorage;

    public CreativeMaterialService(ProjectService projectService, CreativeMaterialRepository materialRepository,
                                   CreativeMaterialFileRepository fileRepository,
                                   @Value("${idea2ip.material-image-dir}") String directory) {
        this.projectService = projectService;
        this.materialRepository = materialRepository;
        this.fileRepository = fileRepository;
        this.imageStorage = new LocalImageStorage(directory, "material-", ErrorResponseCode.INVALID_MATERIAL_IMAGE);
    }

    @Transactional
    public MaterialCreateRes createMaterial(Long userId, Long projectId, MaterialCreateReq req,
                                            List<MultipartFile> files) {
        Project project = projectService.getOwnedProjectForUpdate(userId, projectId);
        if (files.isEmpty()) {
            throw new BaseException(ErrorResponseCode.INVALID_MATERIAL_IMAGE);
        }
        if (req.materialType() != MaterialType.AI_ASSISTED && StringUtils.hasText(req.usedAi())) {
            throw new BaseException(ErrorResponseCode.INVALID_HTTP_MESSAGE_BODY);
        }

        // Use a locking read to see the previous registration even under MySQL's repeatable-read isolation.
        int materialOrder = materialRepository.findFirstByProject_ProjectIdOrderByMaterialOrderDesc(projectId)
                .map(CreativeMaterial::getMaterialOrder)
                .orElse(0) + 1;
        String usedAi = StringUtils.hasText(req.usedAi()) ? req.usedAi() : null;
        CreativeMaterial material = new CreativeMaterial(project, materialOrder, req.materialType(),
                req.title(), req.description(), usedAi, req.productionDate());

        for (MultipartFile file : files) {
            String originalFileName = file.getOriginalFilename();
            if (originalFileName != null) {
                originalFileName = StringUtils.getFilename(originalFileName.replace('\\', '/'));
            }
            if (!StringUtils.hasText(originalFileName) || originalFileName.length() > 255) {
                throw new BaseException(ErrorResponseCode.INVALID_HTTP_MESSAGE_BODY);
            }
            LocalImageStorage.StoredImage image = imageStorage.store(file);
            material.addFile(originalFileName, image.fileName(), image.contentType());
        }

        CreativeMaterial savedMaterial = materialRepository.save(material);
        List<MaterialCreateRes.FileItem> savedFiles = savedMaterial.getFiles().stream()
                .map(file -> new MaterialCreateRes.FileItem(file.getFileId(), file.getOriginalFileName()))
                .toList();
        return new MaterialCreateRes(savedMaterial.getMaterialId(), projectId, savedMaterial.getTitle(), savedFiles);
    }

    @Transactional(readOnly = true)
    public MaterialImage getMaterialImage(Long userId, Long materialId, Long fileId) {
        CreativeMaterial material = materialRepository.findById(materialId)
                .orElseThrow(() -> new BaseException(ErrorResponseCode.MATERIAL_NOT_FOUND));
        projectService.verifyOwner(material.getProject(), userId);

        CreativeMaterialFile file = fileRepository.findByFileIdAndMaterial_MaterialId(fileId, materialId)
                .orElseThrow(() -> new BaseException(ErrorResponseCode.MATERIAL_IMAGE_NOT_FOUND));
        try {
            byte[] content = imageStorage.read(file.getStorageKey());
            return new MaterialImage(MediaType.parseMediaType(file.getFileType()), content);
        } catch (NoSuchFileException e) {
            throw new BaseException(ErrorResponseCode.MATERIAL_IMAGE_NOT_FOUND);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read material image " + fileId, e);
        }
    }

    public record MaterialImage(MediaType contentType, byte[] content) {
    }
}
