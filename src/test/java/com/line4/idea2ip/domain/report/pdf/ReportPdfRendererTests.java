package com.line4.idea2ip.domain.report.pdf;

import com.line4.idea2ip.integration.ai.BriefAiClient.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ReportPdfRendererTests {
    private static final ReportPdfRenderer.Meta META = new ReportPdfRenderer.Meta(LocalDate.of(2026, 11, 12), Set.of());

    private static ReportPdfRenderer renderer() {
        String font = System.getenv("BRIEF_PDF_FONT");
        assumeTrue(font != null && Files.isRegularFile(Path.of(font)), "BRIEF_PDF_FONT required for Korean PDF test");
        return new ReportPdfRenderer(font);
    }

    @Test void numbersAreStableAndTestEditionIsReadable() throws Exception {
        var renderer = renderer();
        var answer = new Evidence("answer:10", "USER_STATEMENT", "직접 수정한 부분", null, "직접 수정한 부분", null, null, null, null, null, null, null);
        var guide = new Evidence("law-test", "OFFICIAL_GUIDE", "가상 테스트 지침", "실제 지침 아님", null,
                "https://www.copyright.or.kr/test", "TEST-ONLY", "2026-01-01", null, "가상 발행기관", "2026-01-01", "2026-01-02");
        var section = new Section("HUMAN_CONTRIBUTION", "인간 기여", List.of("직접 수정: 확정한 내용"), List.of("answer:10", "law-test"));
        String first = text(renderer.render("가상 작품", 3, META, List.of(section), List.of(guide, answer)));
        String second = text(renderer.render("가상 작품", 3, META, List.of(section), List.of(answer, guide)));
        assertEquals(first, second);
        assertTrue(first.contains("1. 인간의 창작적 표현 부분 (사용자 확인)"));
        assertTrue(first.contains("출처: [1], [2] · 사용자 진술 · 공식 지침"));
        assertTrue(first.contains("[1] 사용자 진술 — 직접 수정한 부분"));
        assertEquals(1, first.split("직접 수정한 부분", -1).length - 1);
        assertFalse(first.contains("answer:10"));
        assertTrue(first.contains("가상 발행기관 · TEST-ONLY · 적용 2026-01-01"));
        assertTrue(first.contains("https://www.copyright.or.kr/test"));
    }

    @Test void headerMarksVersionFactsAndFooterCarriesNotice() throws Exception {
        var renderer = renderer();
        var artwork = new Evidence("artwork:3", "ATTACHED_FILE", "사슬과 푸른 시계", null, "codex-clipboard-1234.png",
                null, null, null, null, null, null, null);
        var overview = new Section("WORK_OVERVIEW", "작품 개요", List.of("완성작 설명"), List.of("artwork:3"));
        var ai = new Section("AI_USAGE", "AI 활용", List.of("확인할 정보가 없습니다."), List.of());
        var meta = new ReportPdfRenderer.Meta(LocalDate.of(2026, 11, 12), Set.of("WORK_OVERVIEW"));
        String text = text(renderer.render("가상 작품", 2, meta, List.of(overview, ai), List.of(artwork)));

        assertTrue(text.contains("생성일 2026-11-12  ·  사용자 수정 내용 포함"));
        assertTrue(text.contains("1. 신청할 저작물 전체 개요 (사용자 수정)"));
        assertTrue(text.contains("출처: [1] · 첨부자료 기반"));
        assertTrue(text.contains("2. 활용된 생성형 AI 산출물"));
        assertTrue(text.contains("근거: 미확인"));
        assertFalse(text.contains("codex-clipboard"));
        for (String notice : ReportPdfRenderer.NOTICES) assertTrue(text.replaceAll("\\s+", "").contains(notice.replaceAll("\\s+", "")));
    }

    private String text(byte[] bytes) throws Exception {
        try (var pdf = Loader.loadPDF(bytes)) { return new PDFTextStripper().getText(pdf); }
    }
}
