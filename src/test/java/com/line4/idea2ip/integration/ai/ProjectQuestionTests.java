package com.line4.idea2ip.integration.ai;

import com.line4.idea2ip.domain.interview.entity.InterviewField;
import com.line4.idea2ip.domain.interview.entity.InterviewFieldState;
import com.line4.idea2ip.domain.material.entity.MaterialType;
import com.line4.idea2ip.integration.ai.AiInterviewClient.Context;
import com.line4.idea2ip.integration.ai.AiInterviewClient.Evidence;
import com.line4.idea2ip.integration.ai.AiInterviewClient.InputText;
import com.line4.idea2ip.integration.ai.AiInterviewClient.Work;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.line4.idea2ip.domain.interview.entity.InterviewField.*;
import static com.line4.idea2ip.domain.interview.entity.InterviewFieldState.Status.COMPLETED;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Deployed demo: every project got the same five questions, naming nothing but the tool. Opening questions now name
 * the project's materials, its final artwork and parts of the artwork the user described.
 */
class ProjectQuestionTests {
    private static final String DESCRIPTION = "해 질 무렵 강을 건너는 막차를 그렸습니다. 사선 철교 위로 세 칸 열차가 지나고, "
            + "객실 창문과 강의 반사광에 노란빛을 더했습니다.";
    private static final Work WORK = new Work("막차가 건너는 저녁", DESCRIPTION, List.of("철교", "세 칸 열차", "강", "객실 창문"));
    private static final List<Evidence> MATERIALS = List.of(
            new Evidence("material:1:file:1", MaterialType.AI_ASSISTED, "구도 시안", "AI로 받은 초기 구도", null),
            new Evidence("material:1:file:2", MaterialType.AI_ASSISTED, "구도 시안", "AI로 받은 초기 구도", null),
            new Evidence("material:2:file:3", MaterialType.MANUAL_EDIT, "직접 수정 과정", "철교를 사선으로 바꿈", null));

    private static Context context(List<Evidence> materials, Work work, List<InterviewFieldState> fields) {
        return new Context(1L, "막차", "s", List.of(), materials, List.of(), List.of(), fields, work);
    }

    private static String opening(Context context, InterviewField field) {
        return InterviewQuestionTemplates.draft(context, field, null).content();
    }

    @Test void openingQuestionsNameThisProject() {
        var context = context(MATERIALS, WORK, InterviewFieldState.initialStates());

        assertEquals("‘구도 시안’ 자료를 만들 때 어떤 AI 도구를 사용했나요?", opening(context, AI_TOOL));
        assertEquals("‘구도 시안’ 자료를 만들 때 AI를 어떤 작업에, 어떤 목적으로 사용했나요?", opening(context, AI_PURPOSE));
        assertEquals("AI가 만든 결과를 최종 작품 ‘막차가 건너는 저녁’에 넣었나요? 넣었다면 철교, 세 칸 열차, 강처럼 "
                + "어느 부분에 어떻게 썼는지 알려주세요.", opening(context, AI_OUTPUT_USAGE));
        assertEquals("‘막차가 건너는 저녁’의 철교, 세 칸 열차, 강 같은 요소 중 직접 새로 만든 것이 있다면 무엇이고, "
                + "어떤 방식으로 만들었나요? 없다면 없다고 알려주세요.", opening(context, HUMAN_CREATED_ELEMENT));
        assertEquals("‘직접 수정 과정’ 자료에 남긴 작업처럼, 직접 수정하거나 추가한 표현이 있다면 무엇을 어떻게 작업했나요? "
                + "없다면 없다고 알려주세요.", opening(context, HUMAN_EDITED_EXPRESSION));
    }

    @Test void theToolGivenEarlierIsNamedToo() {
        var fields = InterviewFieldState.initialStates().stream()
                .map(state -> state.field() == AI_TOOL ? new InterviewFieldState(AI_TOOL, COMPLETED, "gpt") : state).toList();
        var context = context(MATERIALS, WORK, fields);

        assertEquals("‘구도 시안’ 자료를 만들 때 ChatGPT 도구를 어떤 작업에, 어떤 목적으로 사용했나요?", opening(context, AI_PURPOSE));
        assertTrue(opening(context, AI_OUTPUT_USAGE).startsWith("ChatGPT 도구로 만든 결과를 최종 작품 ‘막차가 건너는 저녁’에"));
    }

    @Test void twoProjectsGetDifferentQuestions() {
        var other = context(List.of(new Evidence("material:9:file:9", MaterialType.AI_ASSISTED, "꽃병 배치 비교", "", null)),
                new Work("창가의 꽃병", "창가에 놓인 파란 꽃병과 해바라기", List.of("파란 꽃병", "해바라기")),
                InterviewFieldState.initialStates());
        var mine = context(MATERIALS, WORK, InterviewFieldState.initialStates());

        for (InterviewField field : InterviewField.values()) {
            assertNotEquals(opening(mine, field), opening(other, field), field.name());
        }
    }

    @Test void withoutElementsTheTitleIsStillNamed() {
        var context = context(List.of(), new Work("막차가 건너는 저녁", null, List.of()), InterviewFieldState.initialStates());

        assertEquals("최종 작품 ‘막차가 건너는 저녁’에서 직접 새로 만든 요소가 있다면 무엇이고, 어떤 방식으로 만들었나요? "
                + "없다면 없다고 알려주세요.", opening(context, HUMAN_CREATED_ELEMENT));
        assertEquals(MockAiInterviewClient.baseQuestion(AI_TOOL), opening(context, AI_TOOL), "no AI material to name");
    }

    @Test void aProjectWithNothingToNameKeepsTheGenericQuestions() {
        var context = context(List.of(), Work.NONE, InterviewFieldState.initialStates());

        for (InterviewField field : InterviewField.values()) {
            assertEquals(MockAiInterviewClient.baseQuestion(field), opening(context, field), field.name());
        }
    }

    // Deployed: "‘캔 뚜껑 색 수정 전후 자료’ 자료에" repeated the word.
    @Test void aTitleEndingInMaterialIsNotFollowedByItAgain() {
        var materials = List.of(new Evidence("material:1:file:1", MaterialType.AI_ASSISTED, "사과와 캔 배치 비교 자료", "", null),
                new Evidence("material:2:file:2", MaterialType.MANUAL_EDIT, "캔 뚜껑 색 수정 전후 자료", "", null));
        var context = context(materials, Work.NONE, InterviewFieldState.initialStates());

        assertEquals("‘사과와 캔 배치 비교 자료’를 만들 때 어떤 AI 도구를 사용했나요?", opening(context, AI_TOOL));
        assertTrue(opening(context, HUMAN_EDITED_EXPRESSION).startsWith("‘캔 뚜껑 색 수정 전후 자료’에 남긴 작업처럼,"));
    }

    @Test void titlesThatCannotBeQuotedAreLeftOut() {
        var materials = List.of(new Evidence("material:1:file:1", MaterialType.AI_ASSISTED,
                "아주 길게 적은 자료 제목이라서 질문에 넣으면 문장이 너무 길어지는 경우", "", null));
        var quoted = List.of(new Evidence("material:1:file:1", MaterialType.AI_ASSISTED, "‘시안’ 모음", "", null));

        assertEquals(MockAiInterviewClient.baseQuestion(AI_TOOL),
                opening(context(materials, Work.NONE, InterviewFieldState.initialStates()), AI_TOOL));
        assertEquals(MockAiInterviewClient.baseQuestion(AI_TOOL),
                opening(context(quoted, Work.NONE, InterviewFieldState.initialStates()), AI_TOOL));
    }

    @Test void elementsMustStandAsWordsTheUserWrote() {
        var texts = List.of(new InputText("artwork:1", "artwork_description", DESCRIPTION + " 대비를 강조해 표현했습니다."),
                new InputText("artwork:1", "artwork_title", "막차가 건너는 저녁"));

        assertEquals(List.of("해", "막차", "세 칸 열차", "강의 반사광"), ArtworkElements.verified(
                List.of("해", "막차", "열차", "세 칸 열차", "강의 반사광", "달", "작품", "그렸습니다", "Midjourney", "막차"), texts));
        assertEquals(List.of(), ArtworkElements.verified(List.of("조"), texts), "‘조’ only appears inside 강조해");
        assertEquals(List.of("바다"), ArtworkElements.verified(List.of("바다"),
                List.of(new InputText("artwork:1", "artwork_description", "푸른 바다를 그렸다"))), "a noun ending in 다 stays");
    }

    @Test void atMostSixElementsAreKept() {
        var text = List.of(new InputText("artwork:1", "artwork_description", "해, 산, 강, 철교, 열차, 창문, 구름, 별이 있다"));

        assertEquals(6, ArtworkElements.verified(List.of("해", "산", "강", "철교", "열차", "창문", "구름", "별"), text).size());
    }
}
