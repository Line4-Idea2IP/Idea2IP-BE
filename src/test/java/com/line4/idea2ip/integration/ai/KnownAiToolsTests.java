package com.line4.idea2ip.integration.ai;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class KnownAiToolsTests {
    @Test void commonNamesAndSpellingsAreRecognised() {
        assertEquals(Optional.of("ChatGPT"), KnownAiTools.displayName("gpt"));
        assertEquals(Optional.of("ChatGPT"), KnownAiTools.displayName("챗지피티 썼어요"));
        assertEquals(Optional.of("ChatGPT"), KnownAiTools.displayName("GPT-4o로 초안을 만들었어요"));
        assertEquals(Optional.of("Claude"), KnownAiTools.displayName("claude"));
        assertEquals(Optional.of("Gemini"), KnownAiTools.displayName("제미나이로 했어요"));
        assertEquals(Optional.of("Gemini"), KnownAiTools.displayName("Gemini"));
        assertEquals(Optional.of("Stable Diffusion"), KnownAiTools.displayName("sd로 시안 뽑았어요"));
        assertEquals(Optional.of("Claude, Midjourney"), KnownAiTools.displayName("클로드랑 미드저니 썼습니다"));
    }

    @Test void aLongerNameWinsOverTheShortNameInsideIt() {
        var mentions = KnownAiTools.find("Chat GPT랑 SDXL");

        assertEquals(List.of("ChatGPT", "Stable Diffusion"), mentions.stream().map(KnownAiTools.Mention::name).toList());
        assertEquals("Chat GPT", "Chat GPT랑 SDXL".substring(mentions.get(0).start(), mentions.get(0).end()));
    }

    @Test void wordsThatOnlyContainANameAreNotTools() {
        assertTrue(KnownAiTools.find("gptx라는 앱").isEmpty(), "a Latin name must stand as a word");
        assertTrue(KnownAiTools.find("빨리 달리는 모습을 그렸어요").isEmpty(), "everyday words are not tool aliases");
        assertTrue(KnownAiTools.find("직접 그렸습니다").isEmpty());
        assertEquals(Optional.empty(), KnownAiTools.displayName(null));
    }

    @Test void everydayWordsUsedAsAToolAreFoundForConfirmation() {
        assertEquals("Sora", KnownAiTools.ambiguousIn("소라").orElseThrow().name());
        assertEquals("DALL·E", KnownAiTools.ambiguousIn("달리로 그렸어요").orElseThrow().name());
        assertEquals("Bing Image Creator", KnownAiTools.ambiguousIn("빙 썼어요").orElseThrow().name());
        assertEquals("Gemini", KnownAiTools.ambiguousIn("바드요").orElseThrow().name());
        for (String text : List.of("소라색 배경", "빨리 달리는 모습", "빙수 그림", "바드득", "고소라", "미드저니랑 소라")) {
            assertTrue(KnownAiTools.ambiguousIn(text).isEmpty(), text);
        }
    }

    @Test void aConfirmationQuestionNamesItsTool() {
        var sora = KnownAiTools.ambiguousIn("소라").orElseThrow();
        String question = KnownAiTools.confirmationQuestion(sora);

        assertEquals("‘소라’라고 하셨는데, OpenAI의 Sora를 말씀하신 게 맞나요? 맞다면 ‘네’, 아니라면 사용한 도구 이름을 알려주세요.", question);
        assertEquals(Optional.of(sora), KnownAiTools.confirming(question));
        assertEquals(Optional.of(sora), KnownAiTools.confirming("좋아요. " + question), "a lead-in does not hide it");
        assertTrue(KnownAiTools.confirming("‘소라’라고 하셨는데, 어떤 AI 기능을 사용했나요?").isEmpty());
        assertTrue(KnownAiTools.confirming(null).isEmpty());
        var bing = KnownAiTools.ambiguousIn("빙").orElseThrow();
        String bingQuestion = KnownAiTools.confirmationQuestion(bing);
        assertTrue(bingQuestion.startsWith("‘빙’이라고 하셨는데, Microsoft의 Bing Image Creator를"), bingQuestion);
        assertEquals(Optional.of(bing), KnownAiTools.confirming(bingQuestion));
        assertEquals(Optional.of(bing), KnownAiTools.confirming(bingQuestion.replace("’이라고", "’라고")),
                "a confirmation asked before the fix is still read");
        assertEquals(Optional.of("Bing Image Creator"), KnownAiTools.displayName("Bing"));
    }
}
