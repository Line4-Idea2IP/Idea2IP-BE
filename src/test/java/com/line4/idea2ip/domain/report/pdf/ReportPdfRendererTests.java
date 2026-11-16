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

        assertTrue(text.contains("생성일 2026-11-12 · 사용자 수정 있음"));
        assertTrue(text.contains("1. 신청할 저작물 전체 개요"));
        assertFalse(text.contains("(사용자 수정)"));
        assertTrue(text.contains("출처: [1] · 첨부자료 기반"));
        assertTrue(text.contains("2. 활용된 생성형 AI 산출물"));
        assertTrue(text.contains("근거: 미확인"));
        assertFalse(text.contains("codex-clipboard"));
        for (String notice : ReportPdfRenderer.NOTICES) assertTrue(text.replaceAll("\\s+", "").contains(notice.replaceAll("\\s+", "")));
    }

    @Test void linesCiteTheStatementTheyQuote() {
        var edit = new Evidence("answer:50", "USER_STATEMENT", "직접 수정한 부분", "시계판을 파란색으로 칠했습니다.", null,
                null, null, null, null, null, null, null);
        var tool = new Evidence("answer:42", "USER_STATEMENT", "사용한 AI 도구", "Stable Diffusion", null,
                null, null, null, null, null, null, null);
        var section = new Section("AI_USAGE", "AI", List.of(), List.of("answer:42", "answer:50"));
        var labels = java.util.Map.of("answer:42", "[1]", "answer:50", "[2]");
        var byId = java.util.Map.of("answer:42", tool, "answer:50", edit);
        assertEquals("도구: Stable Diffusion, Midjourney [1]\n산출물 설명: Stable Diffusion으로 만든 시안\n직접 수정: 시계판을 파란색으로 칠했습니다. [2]",
                ReportPdfRenderer.cite("도구: Stable Diffusion, Midjourney\n산출물 설명: Stable Diffusion으로 만든 시안"
                        + "\n직접 수정: 시계판을 파란색으로 칠했습니다.", section, labels, byId));
    }

    @Test void shortBlockMovesToTheNextPageWhole() throws Exception {
        var renderer = renderer();
        boolean crossedPage = false;
        // Sweep the block's position so at least one run puts it at the bottom of a page.
        for (int count = 20; count <= 45; count++) {
            var filler = new java.util.ArrayList<String>();
            for (int i = 0; i < count; i++) filler.add("채움 문단 " + i);
            filler.add("항목 첫 줄\n항목 둘째 줄\n항목 셋째 줄\n항목 넷째 줄");
            var section = new Section("EVIDENCE_SUMMARY", "참고자료", filler, List.of());
            try (var pdf = Loader.loadPDF(renderer.render("가상 작품", 1, META, List.of(section), List.of()))) {
                for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                    var stripper = new PDFTextStripper();
                    stripper.setStartPage(page);
                    stripper.setEndPage(page);
                    String text = stripper.getText(pdf);
                    assertEquals(text.contains("항목 첫 줄"), text.contains("항목 넷째 줄"), "block split, count " + count);
                    if (page > 1 && text.startsWith("항목 첫 줄")) crossedPage = true;
                }
            }
        }
        assertTrue(crossedPage, "no run placed the block at a page boundary");
    }

    @Test void templateLayoutDrawsRowsBlockTitlesAndPageNumbers() throws Exception {
        var renderer = renderer();
        var tool = new Evidence("answer:42", "USER_STATEMENT", "사용한 AI 도구", "Stable Diffusion", null,
                null, null, null, null, null, null, null);
        var overview = new Section("WORK_OVERVIEW", "개요", List.of("작품명: 사과와 캔\n작품 유형: 디지털 일러스트"), List.of());
        var ai = new Section("AI_USAGE", "AI", List.of("관련 자료: 구도 자료 (AI 산출물)\n도구: Stable Diffusion"), List.of("answer:42"));
        var human = new Section("HUMAN_CONTRIBUTION", "인간", List.of("구도 자료 (AI 산출물)\n직접 수정: 캔 테두리를 바꿨습니다."), List.of());
        var gaps = new Section("GAPS_AND_GUIDANCE", "보완", List.of("1. 제작일이 없는 자료가 있습니다.", "공식 지침: 판본이 연결되지 않았습니다."), List.of());
        var meta = new ReportPdfRenderer.Meta(LocalDate.of(2026, 11, 16), Set.of("AI_USAGE"),
                java.time.LocalDateTime.of(2026, 11, 16, 14, 5));
        byte[] bytes = renderer.render("사과와 캔", 2, meta, List.of(overview, ai, human, gaps), List.of(tool));
        String text = text(bytes);
        int pages;
        try (var pdf = Loader.loadPDF(bytes)) { pages = pdf.getNumberOfPages(); }

        assertTrue(text.contains("사과와 캔 | v2"));
        assertTrue(text.contains("생성일 2026-11-16 · 사용자 수정 있음 (2026-11-16 14:05)"));
        assertTrue(text.contains("작품명 사과와 캔"), "label and value share a table row");
        assertTrue(text.contains("도구 Stable Diffusion [1]"), "citations stay inside the value cell");
        assertTrue(text.contains("구도 자료 (AI 산출물)\n직접 수정 캔 테두리를 바꿨습니다."));
        assertTrue(text.contains("공식 지침: 판본이 연결되지 않았습니다."));
        assertTrue(text.contains("[1] 사용자 진술 — 사용한 AI 도구"));
        for (int page = 1; page <= pages; page++) assertTrue(text.contains(page + " / " + pages), "page number " + page);
    }

    // Deployed PDF: the overview stores one row per paragraph, and each row was drawn as its own table.
    @Test void rowsStoredAsSeparateParagraphsFormOneTable() throws Exception {
        var renderer = renderer();
        var overview = new Section("WORK_OVERVIEW", "개요", List.of("작품명: 사과와 캔", "작품 유형: 디지털 일러스트",
                "주제·기획 의도: 정물 구성"), List.of());
        var human = new Section("HUMAN_CONTRIBUTION", "인간", List.of("구도 자료 (AI 산출물)\n직접 수정: 캔 테두리를 바꿨습니다.",
                "직접 창작: 사과 단면을 그렸습니다."), List.of());
        byte[] bytes = renderer.render("사과와 캔", 1, META, List.of(overview, human), List.of());

        // One table means one label column: its shaded cells stack without gaps.
        var gaps = new java.util.ArrayList<Float>();
        try (var pdf = Loader.loadPDF(bytes)) {
            var cells = new java.util.ArrayList<float[]>();
            new org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine(pdf.getPage(0)) {
                private final java.util.List<java.awt.geom.Point2D> corners = new java.util.ArrayList<>();
                @Override public void appendRectangle(java.awt.geom.Point2D p0, java.awt.geom.Point2D p1,
                                                      java.awt.geom.Point2D p2, java.awt.geom.Point2D p3) {
                    corners.clear();
                    corners.addAll(List.of(p0, p1, p2, p3));
                }
                @Override public void fillPath(int windingRule) {
                    double bottom = corners.stream().mapToDouble(java.awt.geom.Point2D::getY).min().orElse(0);
                    double top = corners.stream().mapToDouble(java.awt.geom.Point2D::getY).max().orElse(0);
                    cells.add(new float[]{(float) bottom, (float) top});
                }
                @Override public void drawImage(org.apache.pdfbox.pdmodel.graphics.image.PDImage image) {}
                @Override public void clip(int windingRule) {}
                @Override public void moveTo(float x, float y) {}
                @Override public void lineTo(float x, float y) {}
                @Override public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {}
                @Override public java.awt.geom.Point2D getCurrentPoint() { return new java.awt.geom.Point2D.Float(); }
                @Override public void closePath() {}
                @Override public void endPath() {}
                @Override public void strokePath() {}
                @Override public void fillAndStrokePath(int windingRule) {}
                @Override public void shadingFill(org.apache.pdfbox.cos.COSName shadingName) {}
            }.processPage(pdf.getPage(0));
            for (int i = 1; i < cells.size(); i++) gaps.add(cells.get(i - 1)[0] - cells.get(i)[1]);
        }
        // Overview rows 1-3 touch each other; the human block (rows 4-5) is a separate table below them.
        assertEquals(4, gaps.size());
        assertEquals(0f, gaps.get(0), 0.5f);
        assertEquals(0f, gaps.get(1), 0.5f);
        assertTrue(gaps.get(2) > 10, "the overview and the material block are separate tables");
        // The untitled line after the titled block starts its own table instead of joining the material's table.
        assertTrue(gaps.get(3) > 5, "a row without a title is not added to the previous material's table");
    }

    @Test void aClauseShownAsASentenceStillCitesItsAnswer() {
        var created = new Evidence("answer:7", "USER_STATEMENT", "직접 창작한 부분", "사과 단면은 직접 그렸고", null,
                null, null, null, null, null, null, null);
        var section = new Section("HUMAN_CONTRIBUTION", "인간", List.of(), List.of("answer:7"));

        assertEquals("직접 창작: 사과 단면은 직접 그렸습니다. [1]", ReportPdfRenderer.cite("직접 창작: 사과 단면은 직접 그렸습니다.",
                section, java.util.Map.of("answer:7", "[1]"), java.util.Map.of("answer:7", created)));
    }

    @Test void onlyTheSectionsOwnLabelsBecomeRows() {
        var labels = ReportPdfRenderer.ROW_LABELS.get("AI_USAGE");
        assertEquals(new ReportPdfRenderer.Row("산출물 설명 (구도 자료)", "시안 4장"),
                ReportPdfRenderer.row("산출물 설명 (구도 자료): 시안 4장", labels));
        // A sentence the user wrote with a colon stays a paragraph.
        assertNull(ReportPdfRenderer.row("참고: 시안은 작품에 넣지 않았습니다.", labels));
        assertNull(ReportPdfRenderer.row("도구: Midjourney", ReportPdfRenderer.ROW_LABELS.get("HUMAN_CONTRIBUTION")));
    }

    @Test void editNoteShowsWhenTheEditWasSaved() {
        assertEquals("사용자 수정 없음", ReportPdfRenderer.editedNote(new ReportPdfRenderer.Meta(null, Set.of())));
        assertEquals("사용자 수정 있음", ReportPdfRenderer.editedNote(new ReportPdfRenderer.Meta(null, Set.of("AI_USAGE"))));
        assertEquals("사용자 수정 있음 (2026-11-16 09:30)", ReportPdfRenderer.editedNote(new ReportPdfRenderer.Meta(
                null, Set.of("AI_USAGE"), java.time.LocalDateTime.of(2026, 11, 16, 9, 30))));
    }

    @Test void theBoldFaceIsFoundNextToTheRegularOne(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        Path nanum = Files.createFile(dir.resolve("NanumGothic.ttf"));
        assertNull(ReportPdfRenderer.boldFont(nanum, ""), "no bold file: headings fall back to the regular face");
        Path bold = Files.createFile(dir.resolve("NanumGothicBold.ttf"));
        assertEquals(bold, ReportPdfRenderer.boldFont(nanum, ""));
        Path malgun = Files.createFile(dir.resolve("malgun.ttf"));
        Path malgunBold = Files.createFile(dir.resolve("malgunbd.ttf"));
        assertEquals(malgunBold, ReportPdfRenderer.boldFont(malgun, null));
        assertEquals(bold, ReportPdfRenderer.boldFont(malgun, bold.toString()), "a configured path wins");
    }

    private String text(byte[] bytes) throws Exception {
        // The stripper ends lines with the platform separator; compare with "\n" everywhere.
        try (var pdf = Loader.loadPDF(bytes)) { return new PDFTextStripper().getText(pdf).replace("\r\n", "\n"); }
    }
}
