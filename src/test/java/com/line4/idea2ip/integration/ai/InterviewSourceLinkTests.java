package com.line4.idea2ip.integration.ai;

import com.line4.idea2ip.domain.interview.entity.InterviewField;
import com.line4.idea2ip.domain.material.entity.MaterialType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InterviewSourceLinkTests {
    private final Map<Long, MaterialType> types = Map.of(5L, MaterialType.AI_ASSISTED, 6L, MaterialType.MANUAL_EDIT);
    private final List<String> both = List.of("material:5:file:5", "material:6:file:6");

    @Test void answerLinksToMaterialMatchingItsField() {
        assertEquals(6L, ProjectBriefInput.linkedMaterial(both, InterviewField.HUMAN_EDITED_EXPRESSION, types));
        assertEquals(5L, ProjectBriefInput.linkedMaterial(both, InterviewField.AI_PURPOSE, types));
    }

    @Test void ambiguousCitationsDoNotLink() {
        assertNull(ProjectBriefInput.linkedMaterial(both, InterviewField.HUMAN_CREATED_ELEMENT, types));
        assertEquals(6L, ProjectBriefInput.linkedMaterial(List.of("material:6:file:6"),
                InterviewField.HUMAN_CREATED_ELEMENT, types));
        assertNull(ProjectBriefInput.linkedMaterial(List.of(), InterviewField.AI_TOOL, types));
    }
}
