package com.line4.idea2ip.domain.material.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "creative_material_files")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreativeMaterialFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long fileId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reference_material_id", nullable = false)
    private CreativeMaterial material;

    @Column(nullable = false, length = 255)
    private String originalFileName;

    @Column(nullable = false, length = 500)
    private String storageKey;

    @Column(nullable = false, length = 100)
    private String fileType;

    /** When the server received this file; null for files uploaded before the column existed. */
    private LocalDateTime uploadedAt;

    CreativeMaterialFile(CreativeMaterial material, String originalFileName, String storageKey, String fileType) {
        this.material = material;
        this.originalFileName = originalFileName;
        this.storageKey = storageKey;
        this.fileType = fileType;
        this.uploadedAt = LocalDateTime.now();
    }
}
