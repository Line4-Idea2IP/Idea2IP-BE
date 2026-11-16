package com.line4.idea2ip.domain.report;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SourceLabelsTests {
    // Deployed report: "직접 창작: 사과 단면은 직접 그렸고" ended mid-sentence.
    @Test void aClauseCutFromALongerSentenceEndsAsASentence() {
        assertEquals("사과 단면은 직접 그렸습니다.", SourceLabels.asSentence("사과 단면은 직접 그렸고"));
        assertEquals("캔 테두리를 청회색으로 바꿨습니다.", SourceLabels.asSentence("캔 테두리를 청회색으로 바꿨고"));
        assertEquals("시안이 남아 있습니다.", SourceLabels.asSentence("시안이 남아 있고 "));
    }

    @Test void finishedSentencesAndOtherEndingsStayAsWritten() {
        assertEquals("사과 단면을 직접 그렸습니다.", SourceLabels.asSentence("사과 단면을 직접 그렸습니다."));
        assertEquals("사과를 그리고", SourceLabels.asSentence("사과를 그리고"));
        assertEquals("Adobe Firefly", SourceLabels.asSentence("Adobe Firefly"));
        assertNull(SourceLabels.asSentence(null));
    }
}
