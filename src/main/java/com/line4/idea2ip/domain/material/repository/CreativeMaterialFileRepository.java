package com.line4.idea2ip.domain.material.repository;

import com.line4.idea2ip.domain.material.entity.CreativeMaterialFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CreativeMaterialFileRepository extends JpaRepository<CreativeMaterialFile, Long> {

    Optional<CreativeMaterialFile> findByFileIdAndMaterial_MaterialId(Long fileId, Long materialId);
}
