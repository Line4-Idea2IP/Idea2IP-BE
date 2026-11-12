package com.line4.idea2ip.domain.material.dto;

import com.line4.idea2ip.domain.artwork.dto.ArtworkCreateReq;
import com.line4.idea2ip.domain.material.entity.MaterialType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DescriptionLimitTests {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test void materialDescriptionAndTitleAreBounded() {
        String tool = "가".repeat(255);
        assertTrue(validator.validate(new MaterialCreateReq(MaterialType.MANUAL_EDIT, "가".repeat(200),
                "가".repeat(5000), tool, null)).isEmpty());
        assertFalse(validator.validate(new MaterialCreateReq(MaterialType.MANUAL_EDIT, "제목",
                "가".repeat(5001), null, null)).isEmpty());
        assertFalse(validator.validate(new MaterialCreateReq(MaterialType.MANUAL_EDIT, "가".repeat(201),
                null, null, null)).isEmpty());
    }

    @Test void artworkDescriptionIsBounded() {
        assertTrue(validator.validate(new ArtworkCreateReq("작품", "가".repeat(5000))).isEmpty());
        assertFalse(validator.validate(new ArtworkCreateReq("작품", "가".repeat(5001))).isEmpty());
    }

    @Test void largestAllowedMaterialFitsAiSourceLimit() {
        // Mirrors ProjectBriefInput.describe(material): title (type)\ndescription\n사용 도구: tool
        String content = "가".repeat(200) + " (" + MaterialType.AI_ASSISTED + ")" + "\n" + "가".repeat(5000)
                + "\n사용 도구: " + "가".repeat(255);
        assertTrue(content.length() <= 8000);
    }
}
