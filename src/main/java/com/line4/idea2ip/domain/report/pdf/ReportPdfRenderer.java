package com.line4.idea2ip.domain.report.pdf;

import com.line4.idea2ip.domain.report.ReportModels.SectionKey;
import com.line4.idea2ip.domain.report.SourceLabels;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ReportPdfRenderer {
    public static final String TEMPLATE_VERSION = "1.3";
    static final List<String> NOTICES = List.of(
            "이 문서는 저작권 등록 준비용 초안이며 법적 판단을 보장하지 않습니다.",
            "창작 기여와 AI 활용 내용은 사용자 진술과 첨부 자료를 정리한 것이며, 사실 인증이나 저작권 성립 여부의 판단이 아닙니다.");
    // Sections that state facts about the work; without sources they are marked unverified.
    private static final Set<String> FACT_SECTIONS = Set.of("WORK_OVERVIEW", "AI_USAGE", "HUMAN_CONTRIBUTION");

    /** Version facts printed under the title. */
    public record Meta(LocalDate createdOn, Set<String> editedSectionKeys) {
        public Meta {
            editedSectionKeys = editedSectionKeys == null ? Set.of() : Set.copyOf(editedSectionKeys);
        }
    }
    private static final Path WINDOWS_FONT = Path.of("C:/Windows/Fonts/malgun.ttf");
    private final String fontPath;

    public ReportPdfRenderer(@Value("${idea2ip.brief.pdf-font}") String fontPath) {
        this.fontPath = fontPath;
    }

    public byte[] render(String title, int version, Meta meta, List<Section> sections, List<Evidence> sources) throws IOException {
        // Sort by persisted ID so numbering is independent of response/list iteration order.
        List<Evidence> ordered = sources.stream().sorted(Comparator.comparing(Evidence::sourceId)).toList();
        Map<String, String> labels = new LinkedHashMap<>();
        for (Evidence source : ordered) labels.putIfAbsent(source.sourceId(), "[" + (labels.size() + 1) + "]");
        Map<String, Evidence> byId = new LinkedHashMap<>();
        for (Evidence source : ordered) byId.putIfAbsent(source.sourceId(), source);
        Path path = fontPath.isBlank() ? WINDOWS_FONT : Path.of(fontPath);
        try (PDDocument document = new PDDocument(); var fontStream = Files.newInputStream(path);
             var bytes = new ByteArrayOutputStream()) {
            document.getDocumentInformation().setTitle("Copyright Brief - " + title);
            PDType0Font font = PDType0Font.load(document, fontStream);
            try (var layout = new Layout(document, font)) {
                layout.paragraph("Copyright Brief", 22);
                layout.paragraph(title + "  |  v" + version, 13);
                layout.paragraph("생성일 " + (meta.createdOn() == null ? "미확인" : meta.createdOn())
                        + "  ·  " + (meta.editedSectionKeys().isEmpty() ? "사용자 수정 없음" : "사용자 수정 내용 포함"), 10);
                int number = 0;
                for (Section section : sections) {
                    number++;
                    layout.heading(number + ". " + sectionTitle(section)
                            + (meta.editedSectionKeys().contains(section.sectionKey()) ? " (사용자 수정)" : ""));
                    for (String paragraph : section.paragraphs()) {
                        layout.paragraph(FACT_SECTIONS.contains(section.sectionKey())
                                ? cite(paragraph, section, labels, byId) : paragraph, 11);
                    }
                    String evidence = evidenceLine(section, labels, byId);
                    if (evidence != null) layout.paragraph(evidence, 9);
                }
                // Keep the heading with several entries instead of leaving one orphaned at the page bottom.
                if (!sources.isEmpty()) layout.heading("출처 세부 정보", Math.min(sources.size(), 3) * 45f);
                for (Evidence source : ordered) {
                    layout.paragraph(labels.get(source.sourceId()) + " " + SourceLabels.kind(source.kind()) + " — "
                            + SourceLabels.title(source), 10);
                    // Attachment locators are original upload file names; they add noise and may expose local paths.
                    if (source.locator() != null && !"ATTACHED_FILE".equals(source.kind())
                            && !source.locator().strip().equals(source.title().strip())) {
                        layout.paragraph(source.locator(), 9);
                    }
                    if (source.officialUrl() != null) {
                        layout.paragraph(source.publisher() + " · " + source.edition() + " · 적용 " + source.effectiveFrom()
                                + (source.effectiveTo() == null ? "" : " ~ " + source.effectiveTo()), 9);
                        layout.paragraph(source.officialUrl(), 9);
                    }
                }
                layout.heading("안내");
                for (String notice : NOTICES) layout.paragraph(notice, 10);
            }
            document.save(bytes);
            return bytes.toByteArray();
        }
    }

    /**
     * Appends source numbers to each line whose value is a user statement quoted by this section, e.g.
     * "직접 수정: 시계판을 칠했습니다. [5]". Exact matches only, so edited or derived text stays uncited.
     */
    static String cite(String paragraph, Section section, Map<String, String> labels, Map<String, Evidence> byId) {
        var quoted = section.sourceIds().stream().map(byId::get).filter(Objects::nonNull)
                .filter(source -> "USER_STATEMENT".equals(source.kind()) && source.quote() != null
                        && source.quote().strip().length() >= 2)
                .toList();
        if (quoted.isEmpty()) return paragraph;
        var lines = new ArrayList<String>();
        for (String line : paragraph.split("\\R", -1)) {
            int colon = line.indexOf(": ");
            String value = (colon < 0 ? line : line.substring(colon + 2)).strip();
            var parts = List.of(value.split(", "));
            var numbers = quoted.stream()
                    .filter(source -> value.equals(source.quote().strip()) || parts.contains(source.quote().strip()))
                    .map(source -> labels.get(source.sourceId())).filter(Objects::nonNull).distinct()
                    .sorted(Comparator.comparingInt(ReportPdfRenderer::number)).toList();
            lines.add(numbers.isEmpty() ? line : line + " " + String.join("", numbers));
        }
        return String.join("\n", lines);
    }

    /** "[12]" → 12, so source numbers sort numerically. */
    private static int number(String label) {
        return Integer.parseInt(label.substring(1, label.length() - 1));
    }

    private static String sectionTitle(Section section) {
        try {
            return SectionKey.valueOf(section.sectionKey()).title();
        } catch (IllegalArgumentException unknownKey) {
            return section.title();
        }
    }

    /** "출처: [1], [2] · 사용자 진술 · 첨부자료 기반", or "근거: 미확인" for a fact section without sources. */
    private static String evidenceLine(Section section, Map<String, String> labels, Map<String, Evidence> byId) {
        if (section.sourceIds().isEmpty()) {
            return FACT_SECTIONS.contains(section.sectionKey()) ? "근거: 미확인" : null;
        }
        var numbers = section.sourceIds().stream().map(labels::get).filter(Objects::nonNull).distinct()
                .sorted(Comparator.comparingInt(ReportPdfRenderer::number)).collect(Collectors.joining(", "));
        var tags = section.sourceIds().stream().map(byId::get).filter(Objects::nonNull)
                .map(source -> SourceLabels.tag(source.kind())).distinct().collect(Collectors.joining(" · "));
        return "출처: " + numbers + (tags.isEmpty() ? "" : " · " + tags);
    }

    private static final class Layout implements AutoCloseable {
        private static final float MARGIN = 48;
        private static final int KEEP_TOGETHER_ROWS = 12;
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
            var rows = new ArrayList<String>();
            for (String line : text.split("\\R", -1)) {
                var row = new StringBuilder();
                float width = 0;
                for (int offset = 0; offset < line.length();) {
                    int codePoint = line.codePointAt(offset);
                    offset += Character.charCount(codePoint);
                    String glyph = glyph(codePoint);
                    float glyphWidth = font.getStringWidth(glyph) * size / 1000;
                    if (!row.isEmpty() && width + glyphWidth > WIDTH) {
                        rows.add(row.toString());
                        row.setLength(0);
                        width = 0;
                    }
                    row.append(glyph);
                    width += glyphWidth;
                }
                rows.add(row.toString());
            }
            // A short block (one material, one AI use) moves to the next page whole instead of splitting.
            float height = rows.size() * size * 1.55f;
            if (stream != null && rows.size() > 1 && rows.size() <= KEEP_TOGETHER_ROWS && y - height < MARGIN) newPage();
            for (String row : rows) line(row, size);
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
