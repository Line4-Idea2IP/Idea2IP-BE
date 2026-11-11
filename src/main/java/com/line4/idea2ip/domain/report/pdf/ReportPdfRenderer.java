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
import java.util.List;

@Component
public class ReportPdfRenderer {
    public static final String TEMPLATE_VERSION = "1.0";
    private static final Path WINDOWS_FONT = Path.of("C:/Windows/Fonts/malgun.ttf");
    private final String fontPath;

    public ReportPdfRenderer(@Value("${idea2ip.brief.pdf-font}") String fontPath) {
        this.fontPath = fontPath;
    }

    public byte[] render(String title, int version, List<Section> sections, List<Evidence> sources) throws IOException {
        Path path = fontPath.isBlank() ? WINDOWS_FONT : Path.of(fontPath);
        try (PDDocument document = new PDDocument(); var fontStream = Files.newInputStream(path);
             var bytes = new ByteArrayOutputStream()) {
            document.getDocumentInformation().setTitle("Copyright Brief - " + title);
            PDType0Font font = PDType0Font.load(document, fontStream);
            try (var layout = new Layout(document, font)) {
                layout.paragraph("Copyright Brief", 22);
                layout.paragraph(title + "  |  v" + version, 13);
                for (Section section : sections) {
                    layout.heading(section.title());
                    for (String paragraph : section.paragraphs()) layout.paragraph(paragraph, 11);
                    if (!section.sourceIds().isEmpty()) {
                        layout.paragraph("출처: " + String.join(", ", section.sourceIds()), 9);
                    }
                }
                if (!sources.isEmpty()) layout.heading("출처 세부 정보");
                for (Evidence source : sources) {
                    layout.paragraph("[" + source.sourceId() + "] " + source.title(), 10);
                    if (source.locator() != null) layout.paragraph(source.locator(), 9);
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
            if (stream == null || y < MARGIN + 55) newPage();
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
