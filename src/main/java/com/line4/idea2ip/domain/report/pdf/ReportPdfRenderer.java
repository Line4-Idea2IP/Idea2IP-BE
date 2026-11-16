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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Renders the Copyright Brief in the layout of {@code copyright-brief_template.pdf}: label | value tables with a
 * shaded label column, bold headings, a "current / total" page number at the bottom, and a source table at the end.
 *
 * <p>Section paragraphs keep the stored "라벨: 값" lines that the report screen shows and edits. Lines whose label
 * belongs to the section ({@link #ROW_LABELS}) become table rows; a line just before such rows is the block's title;
 * every other line, including text the user rewrote freely, stays an ordinary paragraph.
 */
@Component
public class ReportPdfRenderer {
    public static final String TEMPLATE_VERSION = "2.0";
    static final List<String> NOTICES = List.of(
            "이 문서는 저작권 등록 준비용 초안이며 법적 판단을 보장하지 않습니다.",
            "창작 기여와 AI 활용 내용은 사용자 진술과 첨부 자료를 정리한 것이며, 사실 인증이나 저작권 성립 여부의 판단이 아닙니다.");
    // Sections that state facts about the work; without sources they are marked unverified.
    private static final Set<String> FACT_SECTIONS = Set.of("WORK_OVERVIEW", "AI_USAGE", "HUMAN_CONTRIBUTION");
    /** Labels drawn as table rows, per section. "산출물 설명 (자료명)" counts as "산출물 설명". */
    static final Map<String, List<String>> ROW_LABELS = Map.of(
            "WORK_OVERVIEW", List.of("작품명", "작품 유형", "주제·기획 의도", "최종 작품", "최종 작품 설명"),
            "AI_USAGE", List.of("관련 자료", "도구", "산출물 설명", "사용 목적", "최종 작품 반영"),
            "HUMAN_CONTRIBUTION", List.of("직접 수정", "직접 창작"),
            "EVIDENCE_SUMMARY", List.of("제작일", "설명", "연결된 작업"));
    private static final String OFFICIAL_GUIDE_LABEL = "공식 지침:";
    private static final DateTimeFormatter EDITED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** Version facts printed under the title; {@code editedAt} is when the carried user edit was saved. */
    public record Meta(LocalDate createdOn, Set<String> editedSectionKeys, LocalDateTime editedAt) {
        public Meta {
            editedSectionKeys = editedSectionKeys == null ? Set.of() : Set.copyOf(editedSectionKeys);
        }

        public Meta(LocalDate createdOn, Set<String> editedSectionKeys) {
            this(createdOn, editedSectionKeys, null);
        }
    }

    private static final Path WINDOWS_FONT = Path.of("C:/Windows/Fonts/malgun.ttf");
    private final String fontPath;
    private final String boldFontPath;

    @Autowired
    public ReportPdfRenderer(@Value("${idea2ip.brief.pdf-font}") String fontPath,
                             @Value("${idea2ip.brief.pdf-font-bold:}") String boldFontPath) {
        this.fontPath = fontPath;
        this.boldFontPath = boldFontPath;
    }

    public ReportPdfRenderer(String fontPath) {
        this(fontPath, "");
    }

    public byte[] render(String title, int version, Meta meta, List<Section> sections, List<Evidence> sources) throws IOException {
        // Sort by persisted ID so numbering is independent of response/list iteration order.
        List<Evidence> ordered = sources.stream().sorted(Comparator.comparing(Evidence::sourceId)).toList();
        Map<String, String> labels = new LinkedHashMap<>();
        for (Evidence source : ordered) labels.putIfAbsent(source.sourceId(), "[" + (labels.size() + 1) + "]");
        Map<String, Evidence> byId = new LinkedHashMap<>();
        for (Evidence source : ordered) byId.putIfAbsent(source.sourceId(), source);
        Path regularPath = fontPath.isBlank() ? WINDOWS_FONT : Path.of(fontPath);
        Path boldPath = boldFont(regularPath, boldFontPath);
        try (PDDocument document = new PDDocument(); var bytes = new ByteArrayOutputStream()) {
            document.getDocumentInformation().setTitle("Copyright Brief - " + title);
            PDType0Font regular;
            try (var in = Files.newInputStream(regularPath)) {
                regular = PDType0Font.load(document, in);
            }
            PDType0Font bold = regular;
            if (boldPath != null) {
                try (var in = Files.newInputStream(boldPath)) {
                    bold = PDType0Font.load(document, in);
                }
            }
            try (var layout = new Layout(document, regular, bold)) {
                layout.text("Copyright Brief", 24, regular, Layout.INK);
                layout.text(title + " | v" + version, 13, regular, Layout.INK);
                layout.text("생성일 " + (meta.createdOn() == null ? "미확인" : meta.createdOn()) + " · " + editedNote(meta),
                        10, regular, Layout.MUTED);
                int number = 0;
                for (Section section : sections) {
                    number++;
                    layout.heading(number + ". " + sectionTitle(section));
                    body(layout, section, labels, byId);
                    String evidence = evidenceLine(section, labels, byId);
                    if (evidence != null) layout.text(evidence, 9, regular, Layout.MUTED);
                }
                if (!ordered.isEmpty()) {
                    layout.heading("출처 세부 정보");
                    var rows = new ArrayList<Row>();
                    for (Evidence source : ordered) rows.add(new Row(labels.get(source.sourceId()), sourceDetail(source)));
                    layout.table(null, rows, 44);
                }
                layout.heading("안내");
                for (String notice : NOTICES) layout.text(notice, 10, regular, Layout.INK);
            }
            Layout.numberPages(document, regular);
            document.save(bytes);
            return bytes.toByteArray();
        }
    }

    /** "사용자 수정 없음", or "사용자 수정 있음 (2026-11-16 14:05)". */
    static String editedNote(Meta meta) {
        if (meta.editedSectionKeys().isEmpty()) return "사용자 수정 없음";
        return meta.editedAt() == null ? "사용자 수정 있음" : "사용자 수정 있음 (" + EDITED_AT.format(meta.editedAt()) + ")";
    }

    /** The bold face next to the regular one (NanumGothicBold.ttf, malgunbd.ttf), or null to draw bold text regular. */
    static Path boldFont(Path regular, String configured) {
        if (configured != null && !configured.isBlank()) {
            Path path = Path.of(configured);
            return Files.isRegularFile(path) ? path : null;
        }
        String name = regular.getFileName().toString();
        String boldName = name.equalsIgnoreCase("malgun.ttf") ? "malgunbd.ttf"
                : name.replaceFirst("(?i)\\.ttf$", "Bold.ttf");
        Path sibling = regular.resolveSibling(boldName);
        return !boldName.equals(name) && Files.isRegularFile(sibling) ? sibling : null;
    }

    private static void body(Layout layout, Section section, Map<String, String> labels, Map<String, Evidence> byId)
            throws IOException {
        var rowLabels = ROW_LABELS.getOrDefault(section.sectionKey(), List.of());
        // An untitled table carries on into the next paragraph: the overview stores each row as its own paragraph
        // ("작품명: …", "작품 유형: …"), and they belong in one table. A titled block (one material) ends with its paragraph.
        var rows = new ArrayList<Row>();
        String blockTitle = null;
        for (String paragraph : section.paragraphs()) {
            String text = FACT_SECTIONS.contains(section.sectionKey()) ? cite(paragraph, section, labels, byId) : paragraph;
            String[] lines = text.split("\\R", -1);
            // Consecutive plain lines stay one paragraph, so a short block still moves to the next page whole.
            var plain = new ArrayList<String>();
            for (int i = 0; i < lines.length; i++) {
                Row row = row(lines[i], rowLabels);
                if (row != null) {
                    flush(layout, plain);
                    rows.add(row);
                    continue;
                }
                if (!rows.isEmpty()) layout.table(blockTitle, rows, Layout.LABEL_WIDTH);
                rows.clear();
                blockTitle = null;
                String line = lines[i];
                boolean titlesRows = i + 1 < lines.length && row(lines[i + 1], rowLabels) != null;
                boolean official = line.startsWith(OFFICIAL_GUIDE_LABEL);
                if (!titlesRows && !official) {
                    plain.add(line);
                    continue;
                }
                flush(layout, plain);
                if (titlesRows) {
                    blockTitle = line;
                } else {
                    layout.labeled(OFFICIAL_GUIDE_LABEL, line.substring(OFFICIAL_GUIDE_LABEL.length()).strip());
                }
            }
            flush(layout, plain);
            if (blockTitle != null && !rows.isEmpty()) {
                layout.table(blockTitle, rows, Layout.LABEL_WIDTH);
                rows.clear();
                blockTitle = null;
            }
        }
        if (!rows.isEmpty()) layout.table(blockTitle, rows, Layout.LABEL_WIDTH);
    }

    private static void flush(Layout layout, List<String> plain) throws IOException {
        while (!plain.isEmpty() && plain.getLast().isBlank()) plain.removeLast();
        if (!plain.isEmpty()) layout.text(String.join("\n", plain), 11, null, Layout.INK);
        plain.clear();
    }

    record Row(String label, String value) {}

    /** "도구: Stable Diffusion [1]" → row ("도구", "Stable Diffusion [1]") when the label belongs to the section. */
    static Row row(String line, List<String> rowLabels) {
        int colon = line.indexOf(": ");
        if (colon <= 0) return null;
        String label = line.substring(0, colon).strip();
        boolean known = rowLabels.stream().anyMatch(name -> label.equals(name) || label.startsWith(name + " ("));
        return known ? new Row(label, line.substring(colon + 2).strip()) : null;
    }

    private static String sourceDetail(Evidence source) {
        var lines = new ArrayList<String>();
        lines.add(SourceLabels.kind(source.kind()) + " — " + SourceLabels.title(source));
        // Attachment locators are original upload file names; they add noise and may expose local paths.
        if (source.locator() != null && !"ATTACHED_FILE".equals(source.kind())
                && !source.locator().strip().equals(source.title().strip())) {
            lines.add(source.locator());
        }
        if (source.officialUrl() != null) {
            lines.add(source.publisher() + " · " + source.edition() + " · 적용 " + source.effectiveFrom()
                    + (source.effectiveTo() == null ? "" : " ~ " + source.effectiveTo()));
            lines.add(source.officialUrl());
        }
        return String.join("\n", lines);
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
        static final float[] INK = {0.11f, 0.13f, 0.19f};
        static final float[] MUTED = {0.36f, 0.39f, 0.45f};
        private static final float[] BORDER = {0.86f, 0.88f, 0.92f};
        private static final float[] LABEL_FILL = {0.955f, 0.96f, 0.975f};
        static final float LABEL_WIDTH = 112;
        private static final float MARGIN = 48;
        /** Space kept clear above the page number at the bottom. */
        private static final float FOOTER = 30;
        private static final float PAD = 7;
        private static final float CELL_SIZE = 10.5f;
        private static final float LEADING = 1.55f;
        private static final int KEEP_TOGETHER_ROWS = 12;
        private static final float WIDTH = PDRectangle.A4.getWidth() - MARGIN * 2;
        private static final float TOP = PDRectangle.A4.getHeight() - MARGIN;
        private static final float BOTTOM = MARGIN + FOOTER;
        /** Small notes (a section's "출처:" line) may use the footer band, so they stay under their table. */
        private static final float NOTE_BOTTOM = MARGIN + 6;
        private static final float NOTE_SIZE = 9.5f;
        private final PDDocument document;
        private final PDType0Font regular;
        private final PDType0Font bold;
        private PDPageContentStream stream;
        private float y;
        /** A heading waits for what follows it, so the two always start on the same page. */
        private String pendingHeading;

        private Layout(PDDocument document, PDType0Font regular, PDType0Font bold) {
            this.document = document;
            this.regular = regular;
            this.bold = bold;
        }

        /** Bold section heading; it is drawn together with the next block (see {@link #pendingHeading}). */
        private void heading(String heading) throws IOException {
            drawHeading();
            pendingHeading = heading;
        }

        private float headingHeight() {
            return pendingHeading == null ? 0 : 12 + 15 * LEADING + 7;
        }

        /** Starts a new page unless {@code height} of content, plus any waiting heading, fits on this one. */
        private void reserve(float height) throws IOException {
            reserve(height, BOTTOM);
        }

        private void reserve(float height, float bottom) throws IOException {
            if (stream == null || y - headingHeight() - height < bottom) newPage();
            drawHeading();
        }

        private void drawHeading() throws IOException {
            if (pendingHeading == null) return;
            String heading = pendingHeading;
            pendingHeading = null;
            if (stream == null) newPage();
            y -= 12;
            for (String row : wrap(heading, bold, 15, WIDTH, WIDTH)) {
                write(row, bold, 15, INK, MARGIN, y - 15);
                y -= 15 * LEADING;
            }
            y -= 7;
        }

        /** A wrapped paragraph; a short one moves to the next page whole instead of splitting. */
        private void text(String text, float size, PDType0Font font, float[] color) throws IOException {
            PDType0Font face = font == null ? regular : font;
            var rows = wrap(text, face, size, WIDTH, WIDTH);
            float height = rows.size() * size * LEADING;
            float bottom = size <= NOTE_SIZE ? NOTE_BOTTOM : BOTTOM;
            reserve(rows.size() <= KEEP_TOGETHER_ROWS ? height : size * LEADING, bottom);
            for (String row : rows) {
                if (stream == null || y - size < bottom) newPage();
                write(row, face, size, color, MARGIN, y - size);
                y -= size * LEADING;
            }
            y -= 5;
        }

        /** "공식 지침: …" with the label in bold and the text running on after it. */
        private void labeled(String label, String text) throws IOException {
            float size = 11;
            float labelWidth = width(label + " ", bold, size);
            var rows = wrap(text, regular, size, WIDTH - labelWidth, WIDTH);
            reserve(rows.size() * size * LEADING);
            write(label, bold, size, INK, MARGIN, y - size);
            for (int i = 0; i < rows.size(); i++) {
                if (y - size < BOTTOM) newPage();
                write(rows.get(i), regular, size, INK, i == 0 ? MARGIN + labelWidth : MARGIN, y - size);
                y -= size * LEADING;
            }
            y -= 5;
        }

        /**
         * A label | value table under an optional block title. A table that fits on one page is kept whole; a long
         * one keeps at least its title and first row together, and a row taller than the page continues on the next.
         */
        private void table(String title, List<Row> rows, float labelWidth) throws IOException {
            float lineHeight = CELL_SIZE * LEADING;
            var titleRows = title == null ? List.<String>of() : wrap(title, regular, 11.5f, WIDTH, WIDTH);
            float titleHeight = titleRows.size() * 11.5f * LEADING + (title == null ? 0 : 4);
            var cells = new ArrayList<List<String>[]>();
            float total = titleHeight;
            float first = 0;
            for (Row row : rows) {
                @SuppressWarnings("unchecked")
                List<String>[] cell = new List[]{
                        wrap(row.label(), bold, CELL_SIZE, labelWidth - PAD * 2, labelWidth - PAD * 2),
                        wrap(row.value(), regular, CELL_SIZE, WIDTH - labelWidth - PAD * 2, WIDTH - labelWidth - PAD * 2)};
                cells.add(cell);
                float height = Math.max(cell[0].size(), cell[1].size()) * lineHeight + PAD * 2;
                if (first == 0) first = height;
                total += height;
            }
            float usable = TOP - BOTTOM;
            reserve(total <= usable * 0.6f ? total : titleHeight + first);
            for (String row : titleRows) {
                write(row, regular, 11.5f, INK, MARGIN, y - 11.5f);
                y -= 11.5f * LEADING;
            }
            if (title != null) y -= 4;
            for (var cell : cells) {
                List<String> label = cell[0];
                List<String> value = cell[1];
                int done = 0;
                int lines = Math.max(label.size(), value.size());
                while (done < lines) {
                    int fit = (int) Math.floor((y - BOTTOM - PAD * 2) / lineHeight);
                    if (fit < 1 || (fit < lines - done && fit < 2 && y < TOP - 1)) {
                        newPage();
                        continue;
                    }
                    int count = Math.min(fit, lines - done);
                    float height = count * lineHeight + PAD * 2;
                    float bottom = y - height;
                    fill(MARGIN, bottom, labelWidth, height);
                    stroke(MARGIN, bottom, labelWidth, height);
                    stroke(MARGIN + labelWidth, bottom, WIDTH - labelWidth, height);
                    for (int i = 0; i < count; i++) {
                        float baseline = y - PAD - CELL_SIZE - i * lineHeight + 1;
                        int index = done + i;
                        if (index < label.size()) write(label.get(index), bold, CELL_SIZE, INK, MARGIN + PAD, baseline);
                        if (index < value.size()) write(value.get(index), regular, CELL_SIZE, INK, MARGIN + labelWidth + PAD, baseline);
                    }
                    y = bottom;
                    done += count;
                }
            }
            y -= 8;
        }

        /** Writes "current / total" at the bottom centre of every page once all pages exist. */
        static void numberPages(PDDocument document, PDType0Font font) throws IOException {
            int total = document.getNumberOfPages();
            for (int index = 0; index < total; index++) {
                PDPage page = document.getPage(index);
                String label = (index + 1) + " / " + total;
                float width = font.getStringWidth(label) * 9 / 1000;
                try (var stream = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    stream.beginText();
                    stream.setFont(font, 9);
                    stream.setNonStrokingColor(MUTED[0], MUTED[1], MUTED[2]);
                    stream.newLineAtOffset((PDRectangle.A4.getWidth() - width) / 2, MARGIN - 18);
                    stream.showText(label);
                    stream.endText();
                }
            }
        }

        /** Greedy wrap by code point; {@code firstWidth} applies to the first row of the first line only. */
        private List<String> wrap(String text, PDType0Font font, float size, float firstWidth, float width) throws IOException {
            var rows = new ArrayList<String>();
            for (String line : text.split("\\R", -1)) {
                var row = new StringBuilder();
                float used = 0;
                for (int offset = 0; offset < line.length();) {
                    int codePoint = line.codePointAt(offset);
                    offset += Character.charCount(codePoint);
                    String glyph = glyph(font, codePoint);
                    float glyphWidth = font.getStringWidth(glyph) * size / 1000;
                    float limit = rows.isEmpty() ? firstWidth : width;
                    if (!row.isEmpty() && used + glyphWidth > limit) {
                        rows.add(row.toString());
                        row.setLength(0);
                        used = 0;
                    }
                    // A wrapped row does not start with the space it was broken at.
                    if (row.isEmpty() && codePoint == ' ' && offset > Character.charCount(codePoint)) continue;
                    row.append(glyph);
                    used += glyphWidth;
                }
                rows.add(row.toString());
            }
            return rows;
        }

        private float width(String text, PDType0Font font, float size) throws IOException {
            float total = 0;
            for (int offset = 0; offset < text.length();) {
                int codePoint = text.codePointAt(offset);
                offset += Character.charCount(codePoint);
                total += font.getStringWidth(glyph(font, codePoint)) * size / 1000;
            }
            return total;
        }

        private static String glyph(PDType0Font font, int codePoint) throws IOException {
            if (Character.isISOControl(codePoint)) return " ";
            String value = Character.toString(codePoint);
            try {
                font.encode(value);
                return value;
            } catch (IllegalArgumentException unavailableGlyph) {
                return "?";
            }
        }

        private void write(String text, PDType0Font font, float size, float[] color, float x, float baseline) throws IOException {
            stream.beginText();
            stream.setFont(font, size);
            stream.setNonStrokingColor(color[0], color[1], color[2]);
            stream.newLineAtOffset(x, baseline);
            stream.showText(text);
            stream.endText();
        }

        private void fill(float x, float bottom, float width, float height) throws IOException {
            stream.setNonStrokingColor(LABEL_FILL[0], LABEL_FILL[1], LABEL_FILL[2]);
            stream.addRect(x, bottom, width, height);
            stream.fill();
        }

        private void stroke(float x, float bottom, float width, float height) throws IOException {
            stream.setStrokingColor(BORDER[0], BORDER[1], BORDER[2]);
            stream.setLineWidth(0.6f);
            stream.addRect(x, bottom, width, height);
            stream.stroke();
        }

        private void newPage() throws IOException {
            closeStream();
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = TOP;
        }

        @Override
        public void close() throws IOException {
            drawHeading();
            closeStream();
        }

        private void closeStream() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }
    }
}
