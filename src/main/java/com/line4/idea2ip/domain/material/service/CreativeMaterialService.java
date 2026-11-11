package com.line4.idea2ip.domain.material.service;

import com.line4.idea2ip.domain.material.dto.MaterialCreateReq;
import com.line4.idea2ip.domain.material.dto.MaterialCreateRes;
import com.line4.idea2ip.domain.material.dto.MaterialDetailRes;
import com.line4.idea2ip.domain.material.dto.MaterialListRes;
import com.line4.idea2ip.domain.material.dto.MaterialUpdateReq;
import com.line4.idea2ip.domain.material.entity.CreativeMaterial;
import com.line4.idea2ip.domain.material.entity.MaterialType;
import com.line4.idea2ip.domain.material.repository.CreativeMaterialRepository;
import com.line4.idea2ip.domain.project.entity.Project;
import com.line4.idea2ip.domain.project.service.ProjectService;
import com.line4.idea2ip.global.exception.BaseException;
import com.line4.idea2ip.global.response.code.ErrorResponseCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CreativeMaterialService {

    private final ProjectService projectService;
    private final CreativeMaterialRepository materialRepository;
    private final CreativeMaterialFileService materialFileService;

    @Transactional
    public MaterialCreateRes createMaterial(Long userId, Long projectId, MaterialCreateReq req) {
        Project project = projectService.getOwnedProjectForUpdate(userId, projectId);
        if (req.materialType() != MaterialType.AI_ASSISTED && StringUtils.hasText(req.usedAi())) {
            throw new BaseException(ErrorResponseCode.INVALID_HTTP_MESSAGE_BODY);
        }

        int materialOrder = materialRepository.findFirstByProject_ProjectIdOrderByMaterialOrderDesc(projectId)
                .map(CreativeMaterial::getMaterialOrder)
                .orElse(0) + 1;
        String usedAi = StringUtils.hasText(req.usedAi()) ? req.usedAi() : null;
        CreativeMaterial material = new CreativeMaterial(project, materialOrder, req.materialType(),
                req.title(), req.description(), usedAi, req.productionDate());

        CreativeMaterial savedMaterial = materialRepository.save(material);
        return new MaterialCreateRes(savedMaterial.getMaterialId(), projectId, savedMaterial.getTitle(), List.of());
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
                        materialFileService.getMaterialFiles(material)
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

    // Read attachments committed while waiting for the material lock.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteMaterial(Long userId, Long materialId) {
        Long projectId = materialRepository.findProjectIdByMaterialId(materialId)
                .orElseThrow(() -> new BaseException(ErrorResponseCode.MATERIAL_NOT_FOUND));

        // Match registration's project-first locking order so creation and deletion share one timeline lock.
        projectService.getOwnedProjectForUpdate(userId, projectId);
        CreativeMaterial material = getOwnedMaterialForUpdate(userId, materialId);
        int deletedOrder = material.getMaterialOrder();
        materialFileService.deleteFilesAfterCommit(material.getFiles());

        materialRepository.delete(material);
        materialRepository.flush();
        // Move each row into the free position before shifting the next row.
        for (Long nextMaterialId : materialRepository.findIdsAfterOrder(projectId, deletedOrder)) {
            materialRepository.decreaseOrder(nextMaterialId);
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

    private MaterialDetailRes toMaterialDetail(CreativeMaterial material) {
        return new MaterialDetailRes(
                material.getMaterialId(),
                material.getMaterialType(),
                material.getTitle(),
                material.getDescription(),
                material.getUsedAi(),
                material.getProductionDate(),
                materialFileService.getMaterialFiles(material)
        );
    }
}
