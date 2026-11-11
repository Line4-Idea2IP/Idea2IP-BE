package com.line4.idea2ip.domain.material.service;

import com.line4.idea2ip.domain.material.dto.MaterialCreateReq;
import com.line4.idea2ip.domain.material.dto.MaterialCreateRes;
import com.line4.idea2ip.domain.material.dto.MaterialDetailRes;
import com.line4.idea2ip.domain.material.dto.MaterialFileRes;
import com.line4.idea2ip.domain.material.dto.MaterialListRes;
import com.line4.idea2ip.domain.material.dto.MaterialFilesRes;
import com.line4.idea2ip.domain.material.dto.MaterialUpdateReq;
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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    public MaterialCreateRes createMaterial(Long userId, Long projectId, MaterialCreateReq req) {
        Project project = projectService.getOwnedProjectForUpdate(userId, projectId);
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

        CreativeMaterial savedMaterial = materialRepository.save(material);
        return new MaterialCreateRes(savedMaterial.getMaterialId(), projectId, savedMaterial.getTitle(), List.of());
    }

    @Transactional
    public MaterialFilesRes addMaterialFiles(Long userId, Long materialId, List<MultipartFile> files) {
        CreativeMaterial material = getOwnedMaterialForUpdate(userId, materialId);
        if (files.isEmpty()) {
            throw new BaseException(ErrorResponseCode.INVALID_MATERIAL_IMAGE);
        }

        storeMaterialFiles(material, files);
        materialRepository.flush();
        return new MaterialFilesRes(materialId, getMaterialFiles(material));
    }

    @Transactional
    public MaterialFilesRes updateMaterialFiles(Long userId, Long materialId, List<MultipartFile> newFiles,
                                                List<Long> deleteFileIds) {
        CreativeMaterial material = getOwnedMaterialForUpdate(userId, materialId);
        List<MultipartFile> filesToAdd = newFiles == null ? List.of() : newFiles;
        Set<Long> idsToDelete = deleteFileIds == null ? Set.of() : new HashSet<>(deleteFileIds);
        if (filesToAdd.isEmpty() && idsToDelete.isEmpty()) {
            throw new BaseException(ErrorResponseCode.INVALID_MATERIAL_FILE_CHANGE);
        }

        List<CreativeMaterialFile> filesToDelete = material.getFiles().stream()
                .filter(file -> idsToDelete.contains(file.getFileId()))
                .toList();
        if (filesToDelete.size() != idsToDelete.size()
                || material.getFiles().size() - filesToDelete.size() + filesToAdd.size() < 1) {
            throw new BaseException(ErrorResponseCode.INVALID_MATERIAL_FILE_CHANGE);
        }

        storeMaterialFiles(material, filesToAdd);
        material.removeFiles(filesToDelete);
        for (CreativeMaterialFile file : filesToDelete) {
            imageStorage.deleteAfterCommit(file.getStorageKey());
        }
        materialRepository.flush();
        return new MaterialFilesRes(materialId, getMaterialFiles(material));
    }

    @Transactional(readOnly = true)
    public MaterialListRes getMaterials(Long userId, Long projectId) {
        projectService.getOwnedProject(userId, projectId);

        List<MaterialListRes.MaterialItem> materials = materialRepository
                .findAllByProject_ProjectIdOrderByMaterialOrderAsc(projectId).stream()
                .map(material -> new MaterialListRes.MaterialItem(
                        material.getMaterialId(),
                        material.getMaterialOrder(),
                        material.getTitle(),
                        material.getDescription(),
                        material.getProductionDate(),
                        material.getCreatedAt(),
                        getMaterialFiles(material)
                ))
                .toList();
        return new MaterialListRes(materials);
    }

    @Transactional(readOnly = true)
    public MaterialDetailRes getMaterialDetail(Long userId, Long materialId) {
        CreativeMaterial material = getOwnedMaterial(userId, materialId);
        return toMaterialDetail(material);
    }

    @Transactional
    public MaterialDetailRes updateMaterial(Long userId, Long materialId, MaterialUpdateReq req) {
        CreativeMaterial material = getOwnedMaterialForUpdate(userId, materialId);
        MaterialType materialType = req.getMaterialType() != null ? req.getMaterialType() : material.getMaterialType();
        String title = req.getTitle() != null ? req.getTitle() : material.getTitle();
        String description = req.isDescriptionPresent() ? req.getDescription() : material.getDescription();
        LocalDate productionDate = req.isProductionDatePresent() ? req.getProductionDate() : material.getProductionDate();
        String usedAi = req.isUsedAiPresent() ? req.getUsedAi() : material.getUsedAi();

        if (materialType != MaterialType.AI_ASSISTED && StringUtils.hasText(req.getUsedAi())) {
            throw new BaseException(ErrorResponseCode.INVALID_HTTP_MESSAGE_BODY);
        }
        if (materialType != MaterialType.AI_ASSISTED || !StringUtils.hasText(usedAi)) {
            usedAi = null;
        }

        material.updateInfo(materialType, title, description, usedAi, productionDate);
        return toMaterialDetail(material);
    }

    // Read attachments committed while waiting for the material lock rather than an earlier MySQL snapshot.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteMaterial(Long userId, Long materialId) {
        Long projectId = materialRepository.findProjectIdByMaterialId(materialId)
                .orElseThrow(() -> new BaseException(ErrorResponseCode.MATERIAL_NOT_FOUND));

        // Match registration's project-first locking order so creation and deletion share one timeline lock.
        projectService.getOwnedProjectForUpdate(userId, projectId);
        CreativeMaterial material = getOwnedMaterialForUpdate(userId, materialId);
        int deletedOrder = material.getMaterialOrder();
        for (CreativeMaterialFile file : material.getFiles()) {
            imageStorage.deleteAfterCommit(file.getStorageKey());
        }

        materialRepository.delete(material);
        materialRepository.flush();
        materialRepository.decreaseOrdersAfter(projectId, deletedOrder);
    }

    @Transactional(readOnly = true)
    public MaterialImage getMaterialImage(Long userId, Long materialId, Long fileId) {
        getOwnedMaterial(userId, materialId);
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

    private CreativeMaterial getOwnedMaterial(Long userId, Long materialId) {
        CreativeMaterial material = materialRepository.findById(materialId)
                .orElseThrow(() -> new BaseException(ErrorResponseCode.MATERIAL_NOT_FOUND));
        projectService.verifyOwner(material.getProject(), userId);
        return material;
    }

    private CreativeMaterial getOwnedMaterialForUpdate(Long userId, Long materialId) {
        CreativeMaterial material = materialRepository.findByIdForUpdate(materialId)
                .orElseThrow(() -> new BaseException(ErrorResponseCode.MATERIAL_NOT_FOUND));
        projectService.verifyOwner(material.getProject(), userId);
        return material;
    }

    private void storeMaterialFiles(CreativeMaterial material, List<MultipartFile> files) {
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
    }

    private MaterialDetailRes toMaterialDetail(CreativeMaterial material) {
        return new MaterialDetailRes(
                material.getMaterialId(),
                material.getMaterialType(),
                material.getTitle(),
                material.getDescription(),
                material.getUsedAi(),
                material.getProductionDate(),
                getMaterialFiles(material)
        );
    }

    private List<MaterialFileRes> getMaterialFiles(CreativeMaterial material) {
        return material.getFiles().stream()
                .map(file -> new MaterialFileRes(
                        file.getFileId(),
                        file.getOriginalFileName(),
                        "/api/materials/" + material.getMaterialId() + "/files/" + file.getFileId()
                ))
                .toList();
    }

    public record MaterialImage(MediaType contentType, byte[] content) {
    }
}
