package com.line4.idea2ip.domain.report.pdf;

import com.line4.idea2ip.integration.ai.BriefAiClient.Evidence;
import com.line4.idea2ip.integration.ai.BriefAiClient.Section;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ReportPdfRenderer {
    public static final String TEMPLATE_VERSION = "1.1";
    private static final Path WINDOWS_FONT = Path.of("C:/Windows/Fonts/malgun.ttf");
    private final String fontPath;

    public ReportPdfRenderer(@Value("${idea2ip.brief.pdf-font}") String fontPath) {
        this.fontPath = fontPath;
    }

    public byte[] render(String title, int version, List<Section> sections, List<Evidence> sources) throws IOException {
        // Sort by persisted ID so numbering is independent of response/list iteration order.
        List<Evidence> ordered = sources.stream().sorted(Comparator.comparing(Evidence::sourceId)).toList();
        Map<String, String> labels = new LinkedHashMap<>();
        for (Evidence source : ordered) labels.putIfAbsent(source.sourceId(), "[" + (labels.size() + 1) + "]");
        Path path = fontPath.isBlank() ? WINDOWS_FONT : Path.of(fontPath);
        try (PDDocument document = new PDDocument(); var fontStream = Files.newInputStream(path);
             var bytes = new ByteArrayOutputStream()) {
            document.getDocumentInformation().setTitle("Copyright Brief - " + title);
            PDType0Font font = PDType0Font.load(document, fontStream);
            try (var layout = new Layout(document, font)) {
                layout.paragraph("Copyright Brief", 22);
                layout.paragraph(title + "  |  v" + version, 13);
                for (Section section : sections) {
                    layout.heading(section.sectionKey().equals("HUMAN_CONTRIBUTION")
                            ? "사용자가 확인한 창작 기여" : section.title());
                    for (String paragraph : section.paragraphs()) layout.paragraph(paragraph, 11);
                    if (!section.sourceIds().isEmpty()) {
                        layout.paragraph("출처: " + section.sourceIds().stream().map(labels::get).collect(Collectors.joining(", ")), 9);
                    }
                }
                // Keep the heading with several entries instead of leaving one orphaned at the page bottom.
                if (!sources.isEmpty()) layout.heading("출처 세부 정보", Math.min(sources.size(), 3) * 45f);
                for (Evidence source : ordered) {
                    layout.paragraph(labels.get(source.sourceId()) + " " + kindLabel(source.kind()) + " — " + displayTitle(source), 10);
                    if (source.locator() != null && !source.locator().strip().equals(source.title().strip())) {
                        layout.paragraph(source.locator(), 9);
                    }
                    if (source.officialUrl() != null) {
                        layout.paragraph(source.publisher() + " · " + source.edition() + " · 적용 " + source.effectiveFrom()
                                + (source.effectiveTo() == null ? "" : " ~ " + source.effectiveTo()), 9);
                        layout.paragraph(source.officialUrl(), 9);
                    }
                }
            }
            document.save(bytes);
            return bytes.toByteArray();
        }
    }

    private static String displayTitle(Evidence source) {
        if ("USER_STATEMENT".equals(source.kind())) {
            for (var field : Map.of("AI_TOOL", "사용한 AI 도구", "AI_PURPOSE", "AI 활용 목적",
                    "AI_OUTPUT_USAGE", "AI 결과 활용", "HUMAN_CREATED_ELEMENT", "직접 창작한 부분",
                    "HUMAN_EDITED_EXPRESSION", "직접 수정한 부분").entrySet()) {
                if (source.title().matches("인터뷰 답변 \\d+ \\(" + field.getKey() + "\\)")) return field.getValue();
            }
        }
        return source.title();
    }

    private static String kindLabel(String kind) {
        return switch (kind) {
            case "USER_STATEMENT" -> "인터뷰 답변";
            case "ATTACHED_FILE" -> "프로젝트 자료";
            case "IMAGE_OBSERVATION" -> "이미지 관찰";
            case "OFFICIAL_GUIDE" -> "공식 지침";
            default -> "기타 자료";
        };
    }

    private static final class Layout implements AutoCloseable {
        private static final float MARGIN = 48;
        private static final float WIDTH = PDRectangle.A4.getWidth() - MARGIN * 2;
        private final PDDocument document;
        private final PDType0Font font;
        private PDPageContentStream stream;
        private float y;

        private Layout(PDDocument document, PDType0Font font) {
            this.document = document;
            this.font = font;
        }

        private void heading(String heading) throws IOException {
            heading(heading, 0);
        }

        private void heading(String heading, float keepWithNext) throws IOException {
            if (stream == null || y < MARGIN + 55 + keepWithNext) newPage();
            y -= 8;
            paragraph(heading, 15);
        }

        private void paragraph(String text, float size) throws IOException {
            for (String line : text.split("\\R", -1)) {
                var row = new StringBuilder();
                float width = 0;
                for (int offset = 0; offset < line.length();) {
                    int codePoint = line.codePointAt(offset);
                    offset += Character.charCount(codePoint);
                    String glyph = glyph(codePoint);
                    float glyphWidth = font.getStringWidth(glyph) * size / 1000;
                    if (!row.isEmpty() && width + glyphWidth > WIDTH) {
                        line(row.toString(), size);
                        row.setLength(0);
                        width = 0;
                    }
                    row.append(glyph);
                    width += glyphWidth;
                }
                line(row.toString(), size);
            }
            y -= 5;
        }

        private String glyph(int codePoint) throws IOException {
            if (Character.isISOControl(codePoint)) return " ";
            String value = Character.toString(codePoint);
            try {
                font.encode(value);
                return value;
            } catch (IllegalArgumentException unavailableGlyph) {
                return "?";
            }
        }

        private void line(String text, float size) throws IOException {
            if (stream == null || y < MARGIN + size) newPage();
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(MARGIN, y);
            stream.showText(text);
            stream.endText();
            y -= size * 1.55f;
        }

        private void newPage() throws IOException {
            close();
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = PDRectangle.A4.getHeight() - MARGIN;
        }

        @Override
        public void close() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }
    }
}
