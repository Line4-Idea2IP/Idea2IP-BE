package com.line4.idea2ip.integration.ai;

import com.line4.idea2ip.integration.ai.AiInterviewClient.InputText;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks the artwork elements a model read from the user's descriptions. An element is kept only when it is a
 * short noun phrase standing as its own word in one of the texts ("해 질 무렵" gives "해"; "강조해" gives nothing),
 * so a question never names a part of the artwork the user did not write.
 */
public final class ArtworkElements {
    static final int MAX_ELEMENTS = 6;
    private static final int MAX_LENGTH = 15;
    /** Words that name the artwork or the materials as a whole, not a part of the picture. */
    private static final Set<String> GENERIC = Set.of("작품", "최종 작품", "완성본", "그림", "일러스트", "디지털 일러스트",
            "이미지", "사진", "테스트", "자료", "시안", "파일", "첨부 파일", "인터뷰", "답변", "기능", "검증", "시나리오",
            "가상 시나리오", "구도", "색", "색상", "분위기", "느낌", "모습", "요소", "부분", "화면", "AI", "ai");
    /** What may follow an element where it stands in the text: a particle, then the end of the word. */
    private static final Pattern WORD_END = Pattern.compile(
            "(?:(?:이랑|랑|으로|로|를|을|이|가|은|는|도|와|과|의|에|에서|만|까지|부터|처럼|하고)(?![가-힣])|(?![가-힣]))");
    /** Verb endings ("그렸다", "바꿨어요"), quotes and bare numbers; "바다" or "구름다리" stay. */
    private static final Pattern NOT_A_NOUN = Pattern.compile(
            "(?:[었았였했겠렸웠졌]다|니다|[어아해워]요|세요|죠)$|[‘’“”\"'\\n]|^[\\d\\s\\p{P}]+$");

    private ArtworkElements() {}

    /** The candidates that pass, in their order, without one that a longer kept element contains. */
    public static List<String> verified(List<String> candidates, List<InputText> texts) {
        if (candidates == null || texts == null) return List.of();
        var passed = new ArrayList<String>();
        for (String candidate : candidates) {
            if (candidate == null) continue;
            String element = candidate.strip().replaceAll("\\s+", " ");
            if (element.isEmpty() || element.codePointCount(0, element.length()) > MAX_LENGTH
                    || GENERIC.contains(element) || NOT_A_NOUN.matcher(element).find()
                    || !KnownAiTools.find(element).isEmpty() || passed.contains(element)) continue;
            if (texts.stream().anyMatch(text -> standsIn(element, text.text()))) passed.add(element);
        }
        var kept = new ArrayList<String>();
        for (String element : passed) {
            boolean inLonger = passed.stream().anyMatch(other -> other.length() > element.length() && other.contains(element));
            if (!inLonger && kept.size() < MAX_ELEMENTS) kept.add(element);
        }
        return List.copyOf(kept);
    }

    /** "철교, 세 칸 열차, 강의 반사광": the first {@code limit} elements for a question. */
    static String listed(List<String> elements, int limit) {
        return String.join(", ", elements.subList(0, Math.min(limit, elements.size())));
    }

    private static boolean standsIn(String element, String text) {
        if (text == null) return false;
        for (int at = text.indexOf(element); at >= 0; at = text.indexOf(element, at + 1)) {
            if (at > 0 && isHangul(text.charAt(at - 1))) continue;
            Matcher after = WORD_END.matcher(text).region(at + element.length(), text.length());
            if (after.lookingAt()) return true;
        }
        return false;
    }

    private static boolean isHangul(char c) {
        return c >= 0xAC00 && c <= 0xD7A3;
    }
}
