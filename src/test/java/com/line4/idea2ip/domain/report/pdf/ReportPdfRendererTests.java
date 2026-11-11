package com.line4.idea2ip.domain.report.pdf;

import com.line4.idea2ip.integration.ai.BriefAiClient.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ReportPdfRendererTests {
    @Test void numbersAreStableAndTestEditionIsReadable() throws Exception {
        String font = System.getenv("BRIEF_PDF_FONT");
        assumeTrue(font != null && Files.isRegularFile(Path.of(font)), "BRIEF_PDF_FONT required for Korean PDF test");
        var renderer = new ReportPdfRenderer(font);
        var answer = new Evidence("answer:10", "USER_STATEMENT", "직접 수정한 부분", null, "직접 수정한 부분", null, null, null, null, null, null, null);
        var guide = new Evidence("law-test", "OFFICIAL_GUIDE", "가상 테스트 지침", "실제 지침 아님", null,
                "https://www.copyright.or.kr/test", "TEST-ONLY", "2026-01-01", null, "가상 발행기관", "2026-01-01", "2026-01-02");
        var section = new Section("HUMAN_CONTRIBUTION", "인간 기여", List.of("직접 수정: 확정한 내용"), List.of("answer:10", "law-test"));
        String first = text(renderer.render("가상 작품", 3, List.of(section), List.of(guide, answer)));
        String second = text(renderer.render("가상 작품", 3, List.of(section), List.of(answer, guide)));
        assertEquals(first, second);
        assertTrue(first.contains("사용자가 확인한 창작 기여"));
        assertTrue(first.contains("출처: [1], [2]"));
        assertTrue(first.contains("[1] 인터뷰 답변 — 직접 수정한 부분"));
        assertEquals(1, first.split("직접 수정한 부분", -1).length - 1);
        assertFalse(first.contains("answer:10"));
        assertTrue(first.contains("가상 발행기관 · TEST-ONLY · 적용 2026-01-01"));
        assertTrue(first.contains("https://www.copyright.or.kr/test"));
    }
    private String text(byte[] bytes) throws Exception {
        try (var pdf = Loader.loadPDF(bytes)) { return new PDFTextStripper().getText(pdf); }
    }
}
