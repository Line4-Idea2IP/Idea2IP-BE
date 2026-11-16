package com.line4.idea2ip.domain.interview.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnswerScopeTests {
    // Deployed report: the output-use answer also described the user's own drawing and shading.
    private static final String OUTPUT_ANSWER = "AI 시안은 배치 후보를 비교하는 데만 참고했습니다. 생성 이미지는 작품에 넣지 않았습니다. "
            + "사과 단면은 직접 그렸고 아랫부분에 있는 은빛 캔의 명암은 손으로 조정했습니다.";

    @Test void sentencesOtherFieldsClaimedLeaveTheAskedFieldsQuote() {
        var claimed = List.of("사과 단면은 직접 그렸고", "아랫부분에 있는 은빛 캔의 명암은 손으로 조정했습니다.");

        assertEquals("AI 시안은 배치 후보를 비교하는 데만 참고했습니다. 생성 이미지는 작품에 넣지 않았습니다.",
                AnswerScope.withoutClaimedSentences(OUTPUT_ANSWER, claimed));
    }

    @Test void nothingClaimedKeepsTheQuote() {
        assertEquals(OUTPUT_ANSWER, AnswerScope.withoutClaimedSentences(OUTPUT_ANSWER, List.of()));
        assertEquals(OUTPUT_ANSWER, AnswerScope.withoutClaimedSentences(OUTPUT_ANSWER, List.of("다른 답변의 문장")));
    }

    @Test void aQuoteFullyClaimedIsKeptRatherThanEmptied() {
        String quote = "사과 단면은 직접 그렸습니다.";

        assertEquals(quote, AnswerScope.withoutClaimedSentences(quote, List.of("사과 단면은 직접 그렸습니다")));
    }

    @Test void theLongestUnclaimedRunStaysVerbatim() {
        String quote = "첫 문장입니다. 가져간 문장입니다. 셋째 문장은 조금 더 깁니다. 넷째 문장입니다.";

        String narrowed = AnswerScope.withoutClaimedSentences(quote, List.of("가져간 문장입니다."));

        assertEquals("셋째 문장은 조금 더 깁니다. 넷째 문장입니다.", narrowed);
        assertTrue(quote.contains(narrowed), "the narrowed quote is still the user's own words");
    }
}
