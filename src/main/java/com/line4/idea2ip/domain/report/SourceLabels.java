package com.line4.idea2ip.domain.report;

import com.line4.idea2ip.integration.ai.BriefAiClient.Evidence;

import java.util.Map;
import java.util.regex.Pattern;

/** User-facing names for evidence, shared by the stored report content and the PDF. */
public final class SourceLabels {
    private static final Pattern INTERVIEW_TITLE = Pattern.compile("인터뷰 답변 \\d+ \\(([A-Z_]+)\\)");
    private static final Map<String, String> FIELD_LABELS = Map.of(
            "AI_TOOL", "사용한 AI 도구", "AI_PURPOSE", "AI 활용 목적", "AI_OUTPUT_USAGE", "AI 결과 활용",
            "HUMAN_CREATED_ELEMENT", "직접 창작한 부분", "HUMAN_EDITED_EXPRESSION", "직접 수정한 부분");

    private SourceLabels() {}

    /** Tag matching the UI legend ("사용자 진술", "첨부자료 기반", ...). */
    public static String tag(String kind) {
        return switch (kind) {
            case "USER_STATEMENT" -> "사용자 진술";
            case "ATTACHED_FILE" -> "첨부자료 기반";
            case "IMAGE_OBSERVATION" -> "이미지 관찰";
            case "OFFICIAL_GUIDE" -> "공식 지침";
            default -> "기타 자료";
        };
    }

    /** Short noun for a source list entry. */
    public static String kind(String kind) {
        return switch (kind) {
            case "USER_STATEMENT" -> "사용자 진술";
            case "ATTACHED_FILE" -> "첨부자료";
            case "IMAGE_OBSERVATION" -> "이미지 관찰";
            case "OFFICIAL_GUIDE" -> "공식 지침";
            default -> "기타 자료";
        };
    }

    /** Interview answer titles like "인터뷰 답변 2 (AI_TOOL)" become "사용한 AI 도구"; other titles are kept. */
    public static String title(String title) {
        if (title == null) return "";
        var match = INTERVIEW_TITLE.matcher(title.strip());
        return match.matches() ? FIELD_LABELS.getOrDefault(match.group(1), "인터뷰 답변") : title;
    }

    public static String title(Evidence source) {
        return title(source.title());
    }

    public static boolean isInterviewTitle(String title) {
        return title != null && INTERVIEW_TITLE.matcher(title.strip()).matches();
    }
}
