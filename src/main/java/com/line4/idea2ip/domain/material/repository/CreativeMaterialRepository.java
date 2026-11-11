package com.line4.idea2ip.domain.material.repository;

import com.line4.idea2ip.domain.material.entity.CreativeMaterial;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CreativeMaterialRepository extends JpaRepository<CreativeMaterial, Long> {

    @Query("select m.project.projectId from CreativeMaterial m where m.materialId = :materialId")
    Optional<Long> findProjectIdByMaterialId(@Param("materialId") Long materialId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from CreativeMaterial m where m.materialId = :materialId")
    Optional<CreativeMaterial> findByIdForUpdate(@Param("materialId") Long materialId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CreativeMaterial> findFirstByProject_ProjectIdOrderByMaterialOrderDesc(Long projectId);

    @EntityGraph(attributePaths = "files")
    List<CreativeMaterial> findAllByProject_ProjectIdOrderByMaterialOrderAsc(Long projectId);

    @Query("""
            select m.materialId from CreativeMaterial m
            where m.project.projectId = :projectId and m.materialOrder > :deletedOrder
            order by m.materialOrder
            """)
    List<Long> findIdsAfterOrder(@Param("projectId") Long projectId, @Param("deletedOrder") int deletedOrder);

    @Modifying
    @Query("update CreativeMaterial m set m.materialOrder = m.materialOrder - 1 where m.materialId = :materialId")
    void decreaseOrder(@Param("materialId") Long materialId);
}
