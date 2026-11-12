package com.line4.idea2ip.integration.ai;

import com.line4.idea2ip.domain.interview.entity.InterviewField;
import com.line4.idea2ip.domain.material.entity.MaterialType;
import com.line4.idea2ip.integration.ai.AiInterviewClient.Evidence;
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

    @Test void questionTextNeverShowsInternalSourceIds() {
        var evidence = List.of(new Evidence("material:5:file:5", MaterialType.AI_ASSISTED, "구도 참고 자료", null, null),
                new Evidence("material:6:file:6", MaterialType.MANUAL_EDIT, "색 대비 자료", null, null));
        assertEquals("구도 참고 자료에서 AI를 어떤 목적으로 사용했나요?", LlamaInterviewClient.withoutSourceIds(
                "구도 참고 자료(사용자 자료 sourceId: material:5:file:5)에서 AI를 어떤 목적으로 사용했나요?", evidence));
        assertEquals("'색 대비 자료'를 직접 수정했나요?",
                LlamaInterviewClient.withoutSourceIds("material:6:file:6를 직접 수정했나요?", evidence));
        assertEquals("첨부 자료를 참고했나요?",
                LlamaInterviewClient.withoutSourceIds("material:9:file:9를 참고했나요?", evidence));
    }

    @Test void longTitlesUseGenericNameAndQuestionStaysWithinLimit() {
        var longTitle = "가".repeat(120);
        var evidence = List.of(new Evidence("material:1", MaterialType.AI_ASSISTED, longTitle, null, null),
                new Evidence("material:2", MaterialType.MANUAL_EDIT, "가".repeat(40), null, null));
        assertEquals("첨부 자료를 참고했나요?", LlamaInterviewClient.withoutSourceIds("material:1를 참고했나요?", evidence));

        // A 500-character model question with many short IDs would exceed the limit if every ID became a title.
        String filler = "나".repeat(500 - 20 * 11);
        String question = filler + "material:2 ".repeat(20);
        assertEquals(500, question.length());
        String result = LlamaInterviewClient.withoutSourceIds(question, evidence);
        assertTrue(result.length() <= LlamaInterviewClient.QUESTION_MAX);
        assertFalse(result.contains("material:"));
    }
}
