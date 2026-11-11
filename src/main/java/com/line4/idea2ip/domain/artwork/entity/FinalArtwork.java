package com.line4.idea2ip.domain.artwork.entity;

import com.line4.idea2ip.domain.project.entity.Project;
import com.line4.idea2ip.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "final_artworks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinalArtwork extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "artwork_id")
    private Long artworkId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, unique = true)
    private Project project;

    @Column(nullable = false, columnDefinition = "text")
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "image_path", length = 500)
    private String imagePath;

    @Column(name = "original_file_name", length = 255)
    private String originalFileName;

    public FinalArtwork(Project project, String title, String description) {
        this.project = project;
        this.title = title;
        this.description = description;
    }

    public void updateImage(String imagePath, String originalFileName) {
        this.imagePath = imagePath;
        this.originalFileName = originalFileName;
    }

    public void updateInfo(String title, String description) {
        this.title = title;
        this.description = description;
    }
}
