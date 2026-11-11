package com.line4.idea2ip.domain.material.entity;

import com.line4.idea2ip.domain.project.entity.Project;
import com.line4.idea2ip.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "creative_materials", uniqueConstraints = @UniqueConstraint(
        name = "uk_material_project_order", columnNames = {"project_id", "material_order"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreativeMaterial extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reference_material_id")
    private Long materialId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false)
    private int materialOrder;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private MaterialType materialType;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 255)
    private String usedAi;

    private LocalDate productionDate;

    @OneToMany(mappedBy = "material", cascade = CascadeType.PERSIST, orphanRemoval = true)
    private List<CreativeMaterialFile> files = new ArrayList<>();

    public CreativeMaterial(Project project, int materialOrder, MaterialType materialType,
                            String title, String description, String usedAi, LocalDate productionDate) {
        this.project = project;
        this.materialOrder = materialOrder;
        this.materialType = materialType;
        this.title = title;
        this.description = description;
        this.usedAi = usedAi;
        this.productionDate = productionDate;
    }

    public void addFile(String originalFileName, String storageKey, String fileType) {
        files.add(new CreativeMaterialFile(this, originalFileName, storageKey, fileType));
    }

    public void removeFiles(List<CreativeMaterialFile> filesToRemove) {
        files.removeAll(filesToRemove);
    }

    public void updateInfo(MaterialType materialType, String title, String description,
                           String usedAi, LocalDate productionDate) {
        this.materialType = materialType;
        this.title = title;
        this.description = description;
        this.usedAi = usedAi;
        this.productionDate = productionDate;
    }
}
