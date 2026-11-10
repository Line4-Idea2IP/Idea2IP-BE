package com.line4.idea2ip.domain.material.repository;

import com.line4.idea2ip.domain.material.entity.CreativeMaterial;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface CreativeMaterialRepository extends JpaRepository<CreativeMaterial, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CreativeMaterial> findFirstByProject_ProjectIdOrderByMaterialOrderDesc(Long projectId);
}
