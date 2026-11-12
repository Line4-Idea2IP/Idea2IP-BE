package com.line4.idea2ip.domain.interview.service;

import com.line4.idea2ip.domain.interview.entity.InterviewFieldState;
import com.line4.idea2ip.domain.interview.entity.InterviewFieldState.Status;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.line4.idea2ip.domain.interview.entity.InterviewField.*;
import static org.junit.jupiter.api.Assertions.*;

class InterviewProgressTests {
    private final List<InterviewFieldState> initial = InterviewFieldState.initialStates();

    private static Status status(List<InterviewFieldState> fields, com.line4.idea2ip.domain.interview.entity.InterviewField field) {
        return fields.stream().filter(state -> state.field() == field).findFirst().orElseThrow().status();
    }

    @Test void noAiToolSettlesTheOtherAiFields() {
        var fields = InterviewProgress.apply(initial, new InterviewFieldState(AI_TOOL, Status.NOT_APPLICABLE, "없습니다"),
                List.of("없습니다"));
        assertEquals(Status.NOT_APPLICABLE, status(fields, AI_TOOL));
        assertEquals(Status.NOT_APPLICABLE, status(fields, AI_PURPOSE));
        assertEquals(Status.NOT_APPLICABLE, status(fields, AI_OUTPUT_USAGE));
        assertEquals(InterviewProgress.NO_AI_SUMMARY, fields.get(1).summary());
        // Human work is still asked.
        assertEquals(Status.MISSING, status(fields, HUMAN_CREATED_ELEMENT));
    }

    @Test void answeredAiFieldsAreNotOverwrittenByALaterNoAiAnswer() {
        var answered = initial.stream().map(state -> state.field() == AI_PURPOSE
                ? new InterviewFieldState(AI_PURPOSE, Status.COMPLETED, "구도 비교") : state).toList();
        var fields = InterviewProgress.apply(answered, new InterviewFieldState(AI_TOOL, Status.NOT_APPLICABLE, "없음"),
                List.of("없음"));
        assertEquals(Status.COMPLETED, status(fields, AI_PURPOSE));
    }

    @Test void fieldStopsBeingAskedAfterTheLimit() {
        var evaluation = new InterviewFieldState(AI_PURPOSE, Status.NEEDS_REVIEW, "몰라요");
        assertEquals(Status.NEEDS_REVIEW, status(InterviewProgress.apply(initial, evaluation, List.of("음", "몰라요")), AI_PURPOSE));
        var capped = InterviewProgress.apply(initial, evaluation, List.of("음", "글쎄", "몰라요"));
        // Still unclear: the gap stays visible as NEEDS_REVIEW, but the field no longer holds the interview open.
        assertEquals(Status.NEEDS_REVIEW, status(capped, AI_PURPOSE));
        assertTrue(capped.get(1).closed());
        assertFalse(capped.get(1).needsAnswer());
        assertEquals("음 / 글쎄 / 몰라요", capped.get(1).summary());
    }

    @Test void storedStatesWithoutTheFlagStayOpen() throws Exception {
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        var old = mapper.readValue("{\"field\":\"AI_TOOL\",\"status\":\"NEEDS_REVIEW\",\"summary\":\"x\"}",
                InterviewFieldState.class);
        assertFalse(old.closed());
        assertTrue(old.needsAnswer());
        var roundTrip = mapper.readValue(mapper.writeValueAsString(
                new InterviewFieldState(AI_TOOL, Status.NEEDS_REVIEW, "x", true)), InterviewFieldState.class);
        assertTrue(roundTrip.closed());
    }
}
