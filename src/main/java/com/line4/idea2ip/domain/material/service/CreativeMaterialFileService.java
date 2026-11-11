package com.line4.idea2ip.domain.material.service;

import com.line4.idea2ip.domain.material.dto.MaterialFileRes;
import com.line4.idea2ip.domain.material.dto.MaterialFilesRes;
import com.line4.idea2ip.domain.material.entity.CreativeMaterial;
import com.line4.idea2ip.domain.material.entity.CreativeMaterialFile;
import com.line4.idea2ip.domain.material.repository.CreativeMaterialFileRepository;
import com.line4.idea2ip.domain.material.repository.CreativeMaterialRepository;
import com.line4.idea2ip.domain.project.service.ProjectService;
import com.line4.idea2ip.domain.project.service.ProjectInputChanges;
import com.line4.idea2ip.domain.project.entity.ProjectInputChange.Operation;
import com.line4.idea2ip.domain.project.entity.ProjectInputChange.Type;
import com.line4.idea2ip.global.exception.BaseException;
import com.line4.idea2ip.global.response.code.ErrorResponseCode;
import com.line4.idea2ip.global.storage.LocalImageStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class CreativeMaterialFileService {

    private final ProjectService projectService;
    private final ProjectInputChanges inputChanges;
    private final CreativeMaterialRepository materialRepository;
    private final CreativeMaterialFileRepository fileRepository;
    private final LocalImageStorage imageStorage;

    public CreativeMaterialFileService(ProjectService projectService, CreativeMaterialRepository materialRepository,
                                       CreativeMaterialFileRepository fileRepository, ProjectInputChanges inputChanges,
                                       @Value("${idea2ip.material-image-dir}") String directory) {
        this.projectService = projectService;
        this.inputChanges = inputChanges;
        this.materialRepository = materialRepository;
        this.fileRepository = fileRepository;
        this.imageStorage = new LocalImageStorage(directory, "material-", ErrorResponseCode.INVALID_MATERIAL_IMAGE);
    }

    @Transactional
    public MaterialFilesRes addMaterialFiles(Long userId, Long materialId, List<MultipartFile> files) {
        CreativeMaterial material = getOwnedMaterialForUpdate(userId, materialId);
        if (files.isEmpty()) {
            throw new BaseException(ErrorResponseCode.INVALID_MATERIAL_IMAGE);
        }

        storeMaterialFiles(material, files);
        inputChanges.record(material.getProject(), Type.MATERIAL, Operation.UPDATED, materialId);
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
        deleteFilesAfterCommit(filesToDelete);
        inputChanges.record(material.getProject(), Type.MATERIAL, Operation.UPDATED, materialId);
        materialRepository.flush();
        return new MaterialFilesRes(materialId, getMaterialFiles(material));
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

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteFilesAfterCommit(List<CreativeMaterialFile> files) {
        for (CreativeMaterialFile file : files) {
            imageStorage.deleteAfterCommit(file.getStorageKey());
        }
    }

    public List<MaterialFileRes> getMaterialFiles(CreativeMaterial material) {
        return material.getFiles().stream()
                .map(file -> new MaterialFileRes(
                        file.getFileId(),
                        file.getOriginalFileName(),
                        "/api/materials/" + material.getMaterialId() + "/files/" + file.getFileId()
                ))
                .toList();
    }

    private CreativeMaterial getOwnedMaterialForUpdate(Long userId, Long materialId) {
        Long projectId = materialRepository.findProjectIdByMaterialId(materialId)
                .orElseThrow(() -> new BaseException(ErrorResponseCode.MATERIAL_NOT_FOUND));
        projectService.getOwnedProjectForUpdate(userId, projectId);
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

    public record MaterialImage(MediaType contentType, byte[] content) {
    }
}
