package com.line4.idea2ip.domain.report;

import com.line4.idea2ip.domain.interview.entity.InterviewField;
import com.line4.idea2ip.domain.report.ReportModels.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReportSpecAlignmentTests {
    @Test void allFiveSectionsCanBeEditedInOneRequest() {
        assertTrue(Arrays.stream(SectionKey.values()).allMatch(SectionKey::editable));
        var edits = Arrays.stream(SectionKey.values()).map(key -> new SectionEdit(key, List.of("편집 " + key))).toList();
        var request = new VersionReq(UUID.randomUUID(), Operation.EDIT, 1L, edits, null);
        assertTrue(Validation.buildDefaultValidatorFactory().getValidator().validate(request).isEmpty());
    }

    @Test void pdfFileNameFollowsTheSpec() {
        assertEquals("Idea2IP_달빛정원_CopyrightBrief_v1.pdf", new PdfDownload(1, "달빛 정원", new byte[0]).fileName());
        assertEquals("Idea2IP_ab_CopyrightBrief_v3.pdf", new PdfDownload(3, "a/b:*\n", new byte[0]).fileName());
        assertEquals("Idea2IP_project_CopyrightBrief_v2.pdf", new PdfDownload(2, "  ", new byte[0]).fileName());
    }

    @Test void unclearInterviewFieldsBecomeGapItems() {
        var items = ReportChecklist.items(List.of(), true, "완성작", List.of(), List.of(),
                List.of(InterviewField.HUMAN_EDITED_EXPRESSION));
        assertTrue(items.contains("인터뷰의 '직접 수정한 부분' 답변이 아직 구체적이지 않습니다. 기여 검토에서 해당 내용을 보완해 주세요."));
    }
}
