package com.line4.idea2ip.domain.interview.entity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NoAiAnswerTests {
    @Test void plainNoAiAnswersAreClear() {
        for (String answer : List.of("AI 도구를 사용하지 않았어요.", "사용 안 함", "안 썼어요", "AI 안 씀", "미사용",
                "AI는 쓰지 않았습니다", "사용한 적 없어요", "AI 도구는 없어요", "없음", "없다", "AI는 안 쓰고 직접 그렸어요",
                // Misspellings of "없음" are settled by rule, without a model or a confirmation question.
                "업슴", "없슴.", "업어요", "도구 업슴", "해당 업음")) {
            assertTrue(NoAiAnswer.clearlyNone(answer), answer);
            assertFalse(NoAiAnswer.unclearNone(answer), answer);
        }
    }

    @Test void bareNoAndSymbolsAreConfirmedFirst() {
        for (String answer : List.of("ㄴㄴ", "아니요", "x", "no", "없", "도구 ㄴㄴ")) {
            assertTrue(NoAiAnswer.unclearNone(answer), answer);
            assertFalse(NoAiAnswer.clearlyNone(answer), answer);
        }
    }

    @Test void aNamedToolIsNotANoAiAnswer() {
        for (String answer : List.of("미드저니", "Stable Diffusion을 사용했습니다.", "챗GPT는 안 썼고 미드저니를 사용했어요",
                "스테이블 디퓨젼 썼어요", "생성형 AI는 안 쓰고 포토샵 자동 보정만 썼어요")) {
            assertFalse(NoAiAnswer.clearlyNone(answer), answer);
            assertFalse(NoAiAnswer.unclearNone(answer), answer);
            assertFalse(NoAiAnswer.notAToolName(answer), answer);
        }
    }

    @Test void confirmationRepliesAreReadAsYesOrNo() {
        for (String answer : List.of("네", "예.", "맞아요", "네, 맞습니다", "네 안 썼어요", "ㅇㅇ", "업슴", "사용 안 했어요")) {
            assertTrue(NoAiAnswer.confirms(answer), answer);
            assertFalse(NoAiAnswer.denies(answer), answer);
        }
        for (String answer : List.of("아니요", "아뇨", "ㄴㄴ")) {
            assertTrue(NoAiAnswer.denies(answer), answer);
            assertFalse(NoAiAnswer.confirms(answer), answer);
        }
        assertFalse(NoAiAnswer.confirms("네 미드저니 썼어요"));
        assertFalse(NoAiAnswer.denies("미드저니"));
    }

    @Test void yesNoAndNoneRepliesNameNoTool() {
        for (String answer : List.of("업슴", "네", "아니요", "없습니다", "AI 안 씀", " ")) {
            assertTrue(NoAiAnswer.notAToolName(answer), answer);
        }
    }
}
