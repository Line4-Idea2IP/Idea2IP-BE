package com.line4.idea2ip.domain.contribution;

import com.line4.idea2ip.domain.artwork.entity.FinalArtwork;
import com.line4.idea2ip.domain.material.entity.CreativeMaterial;
import com.line4.idea2ip.domain.material.entity.MaterialType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UploadTimeTests {
    @Test void materialUsesItsLatestFileUploadTime() {
        var material = new CreativeMaterial(null, 1, MaterialType.MANUAL_EDIT, "수정본", null, null, null);
        assertNull(ContributionService.uploadedAt(material));  // no files and not yet persisted
        material.addFile("a.png", "key-a", "image/png");
        assertNotNull(material.getFiles().getFirst().getUploadedAt());
        assertEquals(material.getFiles().getFirst().getUploadedAt(), ContributionService.uploadedAt(material));
    }

    @Test void artworkRecordsWhenTheCurrentImageWasUploaded() {
        var artwork = new FinalArtwork(null, "작품", null);
        assertNull(artwork.getImageUploadedAt());
        artwork.updateImage("img.png", "img.png");
        assertNotNull(artwork.getImageUploadedAt());
        artwork.updateImage(null, null);
        assertNull(artwork.getImageUploadedAt());
    }
}
