package com.line4.idea2ip.domain.interview.entity;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads answers to the AI tool question that say no AI was used. The answer text is never changed; these checks only
 * decide whether it settles the field, needs a confirmation, or can be offered as a tool name.
 * <ul>
 *     <li>clear: "AI 도구를 사용하지 않았어요", "사용 안 함", "AI 안 씀" settle the field as not applicable.</li>
 *     <li>unclear: "업슴", "ㄴㄴ", "아니요" probably mean the same but are confirmed first, because a not-applicable
 *     AI tool also skips the AI purpose and output questions.</li>
 * </ul>
 */
public final class NoAiAnswer {
    private static final Pattern TRAILING = Pattern.compile("[\\s.!?~,…]+$");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    // "AI 도구는", "툴을": the subject of a short "none" answer ("AI 도구는 없어요").
    private static final Pattern TOOL_PREFIX = Pattern.compile("^(?:(?:생성형)?ai|에이아이)?(?:도구|툴|프로그램)?[은는이가을를]?");
    private static final Set<String> NONE = Set.of("해당없음", "없음", "없어요", "없습니다", "없다", "없어", "없었어요",
            "없었습니다", "없음요");
    private static final Set<String> UNCLEAR_NONE = Set.of("업슴", "업음", "없슴", "엄슴", "읍슴", "업어요", "업서요",
            "없서요", "업습니다", "업슴니다", "없슴다", "없슴니다", "없어여", "없어용", "없음다", "없습니당", "업", "없",
            "해당업음", "해당없슴", "해당업슴", "무", "x", "ㄴㄴ", "ㄴ", "노", "노노", "no", "none", "n/a", "na");
    private static final Set<String> NO = Set.of("아니요", "아뇨", "아니오", "아니", "아니에요", "아닙니다", "아님", "ㄴㄴ",
            "노", "노노", "no", "nope");
    private static final Set<String> YES = Set.of("네", "예", "넵", "넹", "녜", "응", "웅", "ㅇㅇ", "ㅇ", "맞아요", "맞아",
            "맞습니다", "맞음", "그래요", "그렇습니다", "그렇다", "yes", "y", "ok", "okay");
    private static final Pattern YES_PREFIX = Pattern.compile("^(?:네|예|넵|응|맞아요|맞습니다)[,]?");
    // Negated use: "사용하지 않", "사용 안", "안 썼", "쓰지 않", "미사용", "사용한 적 없" (matched without spaces).
    private static final Pattern NOT_USED = Pattern.compile(
            "(?:사용|이용|활용)(?:하지(?:는)?않|을?안|은안|한적(?:이)?없|해본적(?:이)?없)|미사용"
                    + "|안(?:썼|씀|쓴|써|쓰|사용|이용|활용)|쓰지(?:는)?않|쓴적(?:이)?없|써본적(?:이)?없");
    // Use that is not negated: "미드저니는 썼고 챗GPT는 안 썼어요" names a tool.
    private static final Pattern USED = Pattern.compile("썼|써서|써봤|써요|씀|(?:사용|이용|활용)(?:했|함|해|하였)");

    private NoAiAnswer() {}

    /** Plainly says no AI tool was used. */
    public static boolean clearlyNone(String answer) {
        String text = compact(answer);
        if (text.isEmpty()) return false;
        if (NONE.contains(text) || NONE.contains(withoutToolPrefix(text))) return true;
        if (!NOT_USED.matcher(text).find()) return false;
        return !USED.matcher(NOT_USED.matcher(text).replaceAll("")).find();
    }

    /** Probably says no AI tool was used, but in a way to confirm before settling the field. */
    public static boolean unclearNone(String answer) {
        String text = compact(answer);
        if (text.isEmpty() || clearlyNone(answer)) return false;
        return UNCLEAR_NONE.contains(text) || UNCLEAR_NONE.contains(withoutToolPrefix(text)) || NO.contains(text);
    }

    /** Agrees with "AI 도구를 사용하지 않았다는 뜻인가요?". */
    public static boolean confirms(String answer) {
        String text = compact(answer);
        if (text.isEmpty() || NO.contains(text)) return false;
        if (YES.contains(text) || clearlyNone(answer) || UNCLEAR_NONE.contains(text)) return true;
        var prefix = YES_PREFIX.matcher(text);
        if (!prefix.find()) return false;
        String rest = text.substring(prefix.end());
        return rest.isEmpty() || YES.contains(rest) || clearlyNone(rest) || UNCLEAR_NONE.contains(rest);
    }

    /** Disagrees with "AI 도구를 사용하지 않았다는 뜻인가요?": a tool was used, but not named. */
    public static boolean denies(String answer) {
        return NO.contains(compact(answer));
    }

    /**
     * An AI tool answer that cannot be a tool's name: a "none" in any form, or a bare yes or no. Such answers stay in
     * the interview transcript but are never offered as the AI tool used.
     */
    public static boolean notAToolName(String answer) {
        return compact(answer).isEmpty() || InterviewFieldState.isNotApplicableAnswer(answer) || clearlyNone(answer)
                || unclearNone(answer) || denies(answer) || confirms(answer);
    }

    private static String withoutToolPrefix(String text) {
        String rest = TOOL_PREFIX.matcher(text).replaceFirst("");
        return rest.isEmpty() ? text : rest;
    }

    private static String compact(String answer) {
        if (answer == null) return "";
        String text = TRAILING.matcher(answer.strip()).replaceAll("");
        return SPACES.matcher(text).replaceAll("").toLowerCase(Locale.ROOT);
    }
}
