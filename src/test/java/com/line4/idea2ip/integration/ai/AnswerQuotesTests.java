package com.line4.idea2ip.integration.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnswerQuotesTests {
    private static final String TYPO_ANSWER = "하단 캔뚜껑 테두리를 회색애서 청회색으루 바궛고 흰색을 얇게 덧칠햇어요.";

    @Test void anExactQuoteIsKeptAsIs() {
        assertEquals("청회색으루 바궛고", AnswerQuotes.locate("청회색으루 바궛고", List.of(TYPO_ANSWER)));
    }

    @Test void aQuoteWhoseTyposTheModelFixedResolvesToTheUsersOwnWords() {
        String corrected = "하단 캔뚜껑 테두리를 회색에서 청회색으로 바꿨고 흰색을 얇게 덧칠했어요";

        assertEquals("하단 캔뚜껑 테두리를 회색애서 청회색으루 바궛고 흰색을 얇게 덧칠햇어요",
                AnswerQuotes.locate(corrected, List.of(TYPO_ANSWER)));
    }

    @Test void theSpanIsFoundInWhicheverAnswerHoldsIt() {
        var answers = List.of("사과 단면을 직접 그렸습니다.", "스테이블 디퓨젼 썻어여. 생성 이미지는 최종작품에 안 넣엇어요.");

        assertEquals("생성 이미지는 최종작품에 안 넣엇어요", AnswerQuotes.locate("생성 이미지는 최종작품에 안 넣었어요", answers));
    }

    @Test void aParaphraseIsNotAQuote() {
        assertNull(AnswerQuotes.locate("캔 윗부분의 색을 파란색으로 다시 칠했습니다", List.of(TYPO_ANSWER)));
    }

    @Test void shortQuotesMustMatchExactly() {
        assertNull(AnswerQuotes.locate("회색에", List.of(TYPO_ANSWER)));
        assertNull(AnswerQuotes.locate("", List.of(TYPO_ANSWER)));
    }
}
