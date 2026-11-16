package com.line4.idea2ip.integration.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Widely used AI tools and the names people write for them ("gpt", "챗지피티", "제미나이"). The model already reads
 * most of these, but a short answer like "gpt" can come back as unresolved, and a quote like "제미나이로" carries its
 * particle into the next question. This list lets code accept such answers and name the tools consistently.
 *
 * <p>Korean aliases that are also everyday words ("달리" for DALL·E, "바드", "빙", "소라") are left out, so an
 * answer that merely contains the word is not taken for a tool. An answer naming only such a word is confirmed
 * first ({@link #ambiguousIn}, {@link #confirmationQuestion}).
 */
public final class KnownAiTools {
    private static final Map<String, List<String>> TOOLS = new LinkedHashMap<>();

    static {
        tool("ChatGPT", "chatgpt", "chat gpt", "gpt-4o", "gpt-4", "gpt4", "gpt-5", "gpt", "챗gpt", "챗지피티", "챗 지피티", "지피티");
        tool("Claude", "claude", "클로드");
        tool("Gemini", "gemini", "제미나이", "제미니", "bard");
        tool("Microsoft Copilot", "copilot", "코파일럿");
        tool("Bing Image Creator", "bing image creator", "bing", "빙 이미지 크리에이터");
        tool("Midjourney", "midjourney", "미드저니", "미드져니");
        tool("Stable Diffusion", "stable diffusion", "stablediffusion", "sdxl", "sd", "스테이블 디퓨전", "스테이블디퓨전",
                "스테이블 디퓨젼", "스테이블디퓨젼");
        tool("DALL·E", "dall-e", "dall·e", "dalle", "달리3");
        tool("Adobe Firefly", "adobe firefly", "firefly", "파이어플라이", "어도비 파이어플라이");
        tool("Leonardo AI", "leonardo", "레오나르도");
        tool("NovelAI", "novelai", "novel ai", "노벨ai");
        tool("Ideogram", "ideogram", "이디오그램");
        tool("Flux", "flux", "플럭스");
        tool("Runway", "runway", "런웨이");
        tool("Sora", "sora");
        tool("Suno", "suno", "수노");
        tool("Canva AI", "canva", "캔바");
        tool("Perplexity", "perplexity", "퍼플렉시티");
        tool("Grok", "grok", "그록");
        tool("CLOVA X", "clova x", "clovax", "클로바x", "클로바 x", "클로바");
        tool("뤼튼", "wrtn", "뤼튼");
        tool("Krea", "krea", "크레아");
        tool("Pika", "pika");
        tool("Kling", "kling", "클링");
    }

    /** Everyday Korean words that are also read as tool names; {@code described} is how the question names the tool. */
    record Ambiguous(String alias, String name, String described) {}

    private static final List<Ambiguous> AMBIGUOUS = List.of(
            new Ambiguous("소라", "Sora", "OpenAI의 Sora"),
            new Ambiguous("달리", "DALL·E", "OpenAI의 DALL·E"),
            new Ambiguous("빙", "Bing Image Creator", "Microsoft의 Bing Image Creator"),
            new Ambiguous("바드", "Gemini", "Google의 Bard(현 Gemini)"));
    /** What may follow an everyday word used as a tool name: a particle or a verb of use ("소라로", "달리 썼어요"). */
    private static final Pattern AMBIGUOUS_END = Pattern.compile(
            "(?:이랑|랑|으로|로|를|을|이|가|도|하고|와|과|이요|요|만|에서|입니다|이에요|예요)(?![가-힣])"
                    + "|사용|이용|활용|썼|썻|써|씀|쓰|(?![가-힣])");
    private static final Pattern CONFIRMATION = Pattern.compile("‘([^’]{1,10})’(?:이)?라고 하셨는데, .{1,60}를 말씀하신 게 맞나요\\?");

    private KnownAiTools() {}

    private static void tool(String name, String... aliases) {
        TOOLS.put(name, List.of(aliases));
    }

    /** One tool mention: its display name and where its alias sits in the text. */
    record Mention(String name, int start, int end) {}

    /** Tool mentions in reading order; a longer alias wins over a shorter one at the same place ("chat gpt" over "gpt"). */
    static List<Mention> find(String text) {
        if (text == null || text.isBlank()) return List.of();
        String lower = text.toLowerCase(Locale.ROOT);
        var found = new ArrayList<Mention>();
        for (var tool : TOOLS.entrySet()) {
            for (String alias : tool.getValue()) {
                for (int at = lower.indexOf(alias); at >= 0; at = lower.indexOf(alias, at + 1)) {
                    if (bounded(lower, at, at + alias.length())) found.add(new Mention(tool.getKey(), at, at + alias.length()));
                }
            }
        }
        found.sort((a, b) -> a.start() != b.start() ? Integer.compare(a.start(), b.start()) : Integer.compare(b.end(), a.end()));
        var kept = new ArrayList<Mention>();
        for (var mention : found) {
            if (kept.isEmpty() || mention.start() >= kept.getLast().end()) kept.add(mention);
        }
        return List.copyOf(kept);
    }

    /** "Claude, Midjourney" for "클로드랑 미드저니"; empty when the text names no known tool. */
    public static Optional<String> displayName(String text) {
        var names = new LinkedHashSet<String>();
        find(text).forEach(mention -> names.add(mention.name()));
        return names.isEmpty() ? Optional.empty() : Optional.of(String.join(", ", names));
    }

    /**
     * The everyday word an answer uses as a tool name ("소라로 영상 만들었어요"), when the answer names no known tool;
     * "소라색", "달리기", "빙수" are not tool names.
     */
    static Optional<Ambiguous> ambiguousIn(String text) {
        if (text == null || text.isBlank() || !find(text).isEmpty()) return Optional.empty();
        for (var tool : AMBIGUOUS) {
            for (int at = text.indexOf(tool.alias()); at >= 0; at = text.indexOf(tool.alias(), at + 1)) {
                int end = at + tool.alias().length();
                if (at > 0 && isHangul(text.charAt(at - 1))) continue;
                Matcher after = AMBIGUOUS_END.matcher(text).region(end, text.length());
                if (after.lookingAt()) return Optional.of(tool);
            }
        }
        return Optional.empty();
    }

    /** "‘소라’라고 하셨는데, OpenAI의 Sora를 말씀하신 게 맞나요? …": asked before an everyday word counts as the tool. */
    static String confirmationQuestion(Ambiguous tool) {
        return InterviewQuestions.quoted(tool.alias()) + " 하셨는데, " + tool.described()
                + "를 말씀하신 게 맞나요? 맞다면 ‘네’, 아니라면 사용한 도구 이름을 알려주세요.";
    }

    /** The tool a question asked the user to confirm, when it is a {@link #confirmationQuestion}. */
    static Optional<Ambiguous> confirming(String question) {
        if (question == null) return Optional.empty();
        Matcher matcher = CONFIRMATION.matcher(question);
        if (!matcher.find()) return Optional.empty();
        return AMBIGUOUS.stream().filter(tool -> tool.alias().equals(matcher.group(1))
                && question.contains(tool.described())).findFirst();
    }

    /**
     * Latin aliases must stand as words ("gpt" not inside "gptx"); Korean aliases must not continue a longer Korean
     * word before them, and may carry a particle after them ("제미나이로").
     */
    private static boolean bounded(String text, int start, int end) {
        boolean latin = Character.isLetterOrDigit(text.charAt(start)) && text.charAt(start) < 128;
        if (start > 0) {
            char before = text.charAt(start - 1);
            if (latin ? isLatinWord(before) : isHangul(before)) return false;
        }
        if (latin && end < text.length() && isLatinWord(text.charAt(end))) return false;
        return true;
    }

    private static boolean isLatinWord(char c) {
        return c < 128 && Character.isLetterOrDigit(c);
    }

    private static boolean isHangul(char c) {
        return c >= 0xAC00 && c <= 0xD7A3;
    }
}
