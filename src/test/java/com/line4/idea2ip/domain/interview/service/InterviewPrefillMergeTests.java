package com.line4.idea2ip.domain.interview.service;

import com.line4.idea2ip.domain.interview.entity.InterviewFieldState;
import com.line4.idea2ip.integration.ai.AiInterviewClient.Finding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.line4.idea2ip.domain.interview.entity.InterviewField.*;
import static com.line4.idea2ip.domain.interview.entity.InterviewFieldState.Status.*;
import static org.junit.jupiter.api.Assertions.*;

class InterviewPrefillMergeTests {
    @Test void aDescriptionSettlesFieldsAndMarksPartialOnesWithWhatIsMissing() {
        var base = InterviewFieldState.initialStates();
        var merged = InterviewPrefillService.merge(base, List.of(
                new Finding(AI_TOOL, COMPLETED, "Midjourney", "artwork:3", null),
                new Finding(AI_OUTPUT_USAGE, NEEDS_REVIEW, "참고만 했어요", "artwork:3", "최종 반영 방식")));

        assertEquals(new InterviewFieldState(AI_TOOL, COMPLETED, "Midjourney"), merged.get(0));
        assertEquals(new InterviewFieldState(AI_PURPOSE, MISSING, null), merged.get(1));
        assertEquals(new InterviewFieldState(AI_OUTPUT_USAGE, NEEDS_REVIEW, "최종 반영 방식"), merged.get(2));
    }

    @Test void aPartialFindingNeverOverridesARegisteredToolCheck() {
        var registered = new InterviewFieldState(AI_TOOL, NEEDS_REVIEW, "등록된 AI 도구: Midjourney");
        var base = List.of(registered);

        var merged = InterviewPrefillService.merge(base, List.of(new Finding(AI_TOOL, NEEDS_REVIEW, "AI", "project:1", "도구 이름")));

        assertEquals(List.of(registered), merged);
    }
}
