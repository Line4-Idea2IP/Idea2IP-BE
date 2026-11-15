package com.line4.idea2ip.integration.ai;

import com.line4.idea2ip.domain.interview.entity.InterviewField;
import com.line4.idea2ip.integration.ai.AiInterviewClient.Answer;
import com.line4.idea2ip.integration.ai.AiInterviewClient.AskedQuestion;
import com.line4.idea2ip.integration.ai.AiInterviewClient.Context;
import com.line4.idea2ip.integration.ai.AiInterviewClient.PreviousTurn;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.line4.idea2ip.domain.interview.entity.InterviewField.*;
import static com.line4.idea2ip.domain.interview.entity.InterviewFieldState.Status.*;
import static org.junit.jupiter.api.Assertions.*;

/** Replays the QA transcript of project 13 (questions and test answers stored in PostgreSQL). */
class InterviewTranscriptTests {
    private static final String[][] TRANSCRIPT = {
            {"AI_TOOL", "창작 과정에서 어떤 AI 도구를 사용했나요?", "Stable Diffusion을 사용했습니다."},
            {"AI_PURPOSE", "AI 도구를 사용해 어떤 작업을 수행했나요?", "구도"},
            {"AI_PURPOSE", "AI로 도움을 얻고 싶었던 작업 한 가지를 알려주세요. 예를 들면 아이디어 구상이나 구도 비교가 있어요.",
                    "초기 구상에서 손의 위치와 방향이 다른 시안을 비교해 화면의 중심을 정하는 데 사용했습니다."},
            {"AI_OUTPUT_USAGE", "AI가 만든 결과를 최종 작품에 활용했나요? 활용했다면 어떤 부분을 어떻게 활용했는지 알려주세요.",
                    "캐릭터의 기본 구도를 참고했고 색상과 표정은 직접 수정했어요. AI 이미지를 최종 작품에 직접 삽입하지 않았습니다."},
            {"HUMAN_CREATED_ELEMENT", "직접 새로 만든 요소가 있다면 무엇이고, 어떤 방식으로 만들었나요? 없다면 없다고 알려주세요.",
                    "사슬의 흐름을 직접 구성했습니다."},
            {"HUMAN_EDITED_EXPRESSION", "(6번 질문)", "시계판을 파란색으로 직접 칠했습니다."},
    };

    /** Context after the first {@code turns} transcript turns. */
    private static Context after(int turns) {
        var questions = new ArrayList<AskedQuestion>();
        var answers = new ArrayList<Answer>();
        var history = new ArrayList<PreviousTurn>();
        for (int i = 0; i < turns; i++) {
            var field = InterviewField.valueOf(TRANSCRIPT[i][0]);
            questions.add(new AskedQuestion(field, TRANSCRIPT[i][1]));
            answers.add(new Answer((long) i + 1, field, TRANSCRIPT[i][2], TRANSCRIPT[i][1]));
            history.add(new PreviousTurn(field, TRANSCRIPT[i][1], TRANSCRIPT[i][2]));
        }
        return new Context(13L, "사슬과 푸른 시계", "snapshot", answers, List.of(), questions, history);
    }

    /** A live client pointed at a closed port: any model call fails, proving the question needed no model. */
    private static LlamaInterviewClient offlineLiveClient() {
        return new LlamaInterviewClient("http://127.0.0.1:9/v1", "test-model", JsonMapper.builder().build(),
                new MockAiInterviewClient());
    }

    @Test void anUnclearNoAiAnswerIsConfirmedBeforeSkippingAiQuestions() {
        var base = MockAiInterviewClient.baseQuestion(AI_TOOL);
        var typo = new Answer(1L, AI_TOOL, "ㄴㄴ", base);
        var afterTypo = new Context(1L, "작품", "s", List.of(typo), List.of(),
                List.of(new AskedQuestion(AI_TOOL, base)), List.of(new PreviousTurn(AI_TOOL, base, "ㄴㄴ")));
        var mock = new MockAiInterviewClient();
        // Not a tool name and not yet "no AI": the field stays open for one confirmation.
        assertEquals(NEEDS_REVIEW, mock.analyzeAnswer(afterTypo, typo).status());
        for (AiInterviewClient client : List.of(mock, offlineLiveClient())) {
            var draft = client.generateQuestion(afterTypo, AI_TOOL);
            assertEquals(InterviewQuestions.NO_AI_CONFIRMATION, draft.content());
            assertEquals(InterviewQuestions.NO_AI_CONFIRMATION_REASON, draft.reason());
        }

        String confirm = InterviewQuestions.NO_AI_CONFIRMATION;
        var asked = List.of(new AskedQuestion(AI_TOOL, base), new AskedQuestion(AI_TOOL, confirm));
        var yes = new Answer(2L, AI_TOOL, "네", confirm);
        var afterYes = new Context(1L, "작품", "s", List.of(typo, yes), List.of(), asked, List.of());
        var settled = mock.analyzeAnswer(afterYes, yes);
        assertEquals(NOT_APPLICABLE, settled.status());
        assertEquals(InterviewQuestions.NO_AI_CONFIRMED, settled.summary());

        // "아니요": AI was used, so the tool name is asked again (not the confirmation).
        var no = new Answer(2L, AI_TOOL, "아니요", confirm);
        var afterNo = new Context(1L, "작품", "s", List.of(typo, no), List.of(), asked, List.of());
        assertEquals(NEEDS_REVIEW, mock.analyzeAnswer(afterNo, no).status());
        for (AiInterviewClient client : List.of(mock, offlineLiveClient())) {
            String next = client.generateQuestion(afterNo, AI_TOOL).content();
            assertNotEquals(confirm, next);
            assertFalse(InterviewQuestions.alreadyAsked(next, List.of(base, confirm)));
        }
        // A tool named in reply to the confirmation completes the field.
        var named = new Answer(2L, AI_TOOL, "미드저니 썼어요", confirm);
        assertEquals(COMPLETED, mock.analyzeAnswer(
                new Context(1L, "작품", "s", List.of(typo, named), List.of(), asked, List.of()), named).status());
    }

    @Test void aClearNoAiAnswerSettlesTheToolAtOnce() {
        var base = MockAiInterviewClient.baseQuestion(AI_TOOL);
        for (String text : List.of("AI 도구를 사용하지 않았어요", "사용 안 함", "AI 안 씀", "없음")) {
            var answer = new Answer(1L, AI_TOOL, text, base);
            var context = new Context(1L, "작품", "s", List.of(answer), List.of());
            // The live client applies the same rule; the closed port proves no model is called.
            for (AiInterviewClient client : List.of(new MockAiInterviewClient(), offlineLiveClient())) {
                assertEquals(NOT_APPLICABLE, client.analyzeAnswer(context, answer).status(), text);
            }
        }
        // A bare "네" names no tool; it is not saved as one.
        var yes = new Answer(1L, AI_TOOL, "네", base);
        assertEquals(NEEDS_REVIEW, new MockAiInterviewClient().analyzeAnswer(
                new Context(1L, "작품", "s", List.of(yes), List.of()), yes).status());
    }

    @Test void humanEditsOpenWithTheEditMentionedDuringAiOutputUse() {
        // Only the quoted work is asked about, so "없음" cannot be read as "no edits at all".
        String expected = "‘색상과 표정은 직접 수정했어요’라고 하셨는데, 어떤 부분을 어떻게 바꾸셨나요?";
        for (AiInterviewClient client : List.of(new MockAiInterviewClient(), offlineLiveClient())) {
            var draft = client.generateQuestion(after(5), HUMAN_EDITED_EXPRESSION);
            assertEquals(expected, draft.content());
            assertEquals(InterviewQuestions.OPENING_REASON, draft.reason());
        }
    }

    @Test void supplementQuotesTheAnswerWithoutPresupposingAPosition() {
        for (AiInterviewClient client : List.of(new MockAiInterviewClient(), offlineLiveClient())) {
            String question = client.generateQuestion(after(6), HUMAN_EDITED_EXPRESSION).content();
            assertTrue(question.startsWith("‘시계판을 파란색으로 직접 칠했습니다’라고 하셨는데, 그렇게 정한 이유가 있나요?"), question);
            assertFalse(question.contains("그 위치"), question);
        }
    }

    @Test void humanCreationIsNotOpenedWithTheEditStatement() {
        // "직접 수정" belongs to human edits; the creation field keeps its own opening.
        assertTrue(InterviewQuestions.relatedStatement(after(4), HUMAN_CREATED_ELEMENT).isEmpty());
        assertEquals(MockAiInterviewClient.baseQuestion(HUMAN_CREATED_ELEMENT),
                new MockAiInterviewClient().generateQuestion(after(4), HUMAN_CREATED_ELEMENT).content());
    }

    @Test void onlyTheUsersOwnUnnegatedUncorrectedWorkOpensAField() {
        assertTrue(statement("AI가 색을 수정했어요.").isEmpty());              // not the user's work
        assertTrue(statement("AI가 색상을 직접 수정했어요.").isEmpty());          // "직접" but the AI did it
        assertTrue(statement("작가가 손으로 색을 칠했어요.").isEmpty());          // someone else did it
        assertTrue(statement("색이 직접 수정됐어요.").isEmpty());                // passive: unclear who did it
        assertTrue(statement("색은 직접 수정하지 않았어요.").isEmpty());          // negated
        assertEquals("표정은 직접 바꿨어요", statement("구도는 참고했고, 표정은 직접 바꿨어요.").orElseThrow());
        // Mentioning AI output as the material is fine; the user is still the one editing.
        assertEquals("AI 결과를 참고해 표정은 직접 수정했어요",
                statement("AI 결과를 참고해 표정은 직접 수정했어요.").orElseThrow());

        // A later "없습니다" in the same field may correct the statement, so it is not used as a premise.
        var turns = List.of(new PreviousTurn(AI_OUTPUT_USAGE, "어떻게 활용했나요?", "색상은 직접 수정했어요."),
                new PreviousTurn(AI_OUTPUT_USAGE, "직접 수정한 게 맞나요?", "없습니다"));
        var corrected = new Context(1L, "작품", "s", List.of(), List.of(), List.of(), turns);
        assertTrue(InterviewQuestions.relatedStatement(corrected, HUMAN_EDITED_EXPRESSION).isEmpty());
    }

    @Test void topicsAndAdverbsDoNotHideTheUsersOwnWork() {
        // Common phrasings in this service: the topic names what was worked on, not who worked on it.
        assertEquals("색은 직접 칠했어요", statement("AI 이미지는 참고만 하고 색은 직접 칠했어요.").orElseThrow());
        assertEquals("배경은 직접 칠했어요", statement("배경은 직접 칠했어요.").orElseThrow());
        assertEquals("손의 각도는 직접 수정했어요", statement("손의 각도는 직접 수정했어요.").orElseThrow());
        assertEquals("많이 직접 수정했어요", statement("많이 직접 수정했어요.").orElseThrow());
        assertEquals("표정은 직접 바꿨어요", statement("처음에는 시안을 쓰고 표정은 직접 바꿨어요.").orElseThrow());
        // "시안이" is a subject but names part of the work, not another actor.
        assertEquals("시안이 마음에 들어서 색은 직접 칠했어요", statement("시안이 마음에 들어서 색은 직접 칠했어요.").orElseThrow());
        assertEquals("제가 시계판을 직접 칠했어요", statement("제가 시계판을 직접 칠했어요.").orElseThrow());
    }

    @Test void namedOrListedActorsStillRuleTheStatementOut() {
        assertTrue(statement("민수가 색상을 직접 수정했어요.").isEmpty());
        assertTrue(statement("선생님께서 손으로 고쳐 주셨어요.").isEmpty());
        assertTrue(statement("디자이너는 색을 직접 칠했어요.").isEmpty());
        // A clause after a named actor keeps that actor until the speaker names themself.
        assertTrue(statement("민수가 구도를 잡고 색은 직접 칠했어요.").isEmpty());
        assertEquals("제가 색은 직접 칠했어요", statement("민수가 구도를 잡고 제가 색은 직접 칠했어요.").orElseThrow());
    }

    @Test void anUnclearActorIsQuotedButAskedAboutFirst() {
        var named = related("민수는 색을 직접 칠했어요.").orElseThrow();
        assertFalse(named.ownWork());
        // The doubt about an unclear topic carries into the next clause until the speaker names themself.
        var carried = related("민수는 구도를 잡고 색은 직접 칠했어요.").orElseThrow();
        assertEquals("색은 직접 칠했어요", carried.statement());
        assertFalse(carried.ownWork());
        assertTrue(related("민수는 구도를 잡고 제가 색은 직접 칠했어요.").orElseThrow().ownWork());
        assertEquals("‘민수는 색을 직접 칠했어요’라고 하셨는데, 이 수정은 직접 하신 건가요? "
                + "직접 하셨다면 어떤 부분을 어떻게 바꾸셨는지 알려주세요.",
                InterviewQuestions.opening(HUMAN_EDITED_EXPRESSION, named));
        assertFalse(related("포즈는 직접 바꿨어요.").orElseThrow().ownWork());     // a person or a part: unclear

        // Clear cases still ask about the method directly.
        assertTrue(related("제가 포즈는 직접 바꿨어요.").orElseThrow().ownWork());
        assertTrue(related("처음에는 시안을 쓰고 표정은 직접 바꿨어요.").orElseThrow().ownWork());
        assertTrue(related("배경은 직접 칠했어요.").orElseThrow().ownWork());

        // The who-question is still an opening: a bare "없음" leaves the field for review.
        String whoQuestion = InterviewQuestions.opening(HUMAN_EDITED_EXPRESSION, named);
        assertTrue(InterviewQuestions.isOpeningQuestion(HUMAN_EDITED_EXPRESSION, whoQuestion));
        var reply = new Answer(1L, HUMAN_EDITED_EXPRESSION, "없음", whoQuestion);
        var state = new MockAiInterviewClient().analyzeAnswer(
                new Context(1L, "작품", "s", List.of(reply), List.of()), reply);
        assertEquals(com.line4.idea2ip.domain.interview.entity.InterviewFieldState.Status.NEEDS_REVIEW, state.status());
    }

    @Test void anVerbNegationIsNotQuotedAsWork() {
        assertTrue(statement("색은 제가 직접 안 칠했어요.").isEmpty());
        assertTrue(statement("표정은 직접 안고쳤어요.").isEmpty());
        assertTrue(statement("제가 직접 안 바꿨어요.").isEmpty());
        // "안에" is a place, not a negation.
        assertEquals("시계판 안에 색을 직접 칠했어요", statement("시계판 안에 색을 직접 칠했어요.").orElseThrow());

        // A later "안 고쳤어요" corrects an earlier statement in the same field.
        var turns = List.of(new PreviousTurn(AI_OUTPUT_USAGE, "어떻게 활용했나요?", "색은 직접 수정했어요."),
                new PreviousTurn(AI_OUTPUT_USAGE, "어느 단계였나요?", "다시 생각해 보니 색은 안 고쳤어요."));
        var corrected = new Context(1L, "작품", "s", List.of(), List.of(), List.of(), turns);
        assertTrue(InterviewQuestions.relatedStatement(corrected, HUMAN_EDITED_EXPRESSION).isEmpty());
    }

    @Test void aSentenceCorrectionLaterMakesTheStatementUnresolved() {
        for (String correction : List.of("정정할게요. 제가 수정하지 않았어요.", "제가 수정한 게 아니에요. 사실은 그대로 썼어요.")) {
            var turns = List.of(new PreviousTurn(AI_OUTPUT_USAGE, "어떻게 활용했나요?", "색을 직접 수정했어요."),
                    new PreviousTurn(AI_OUTPUT_USAGE, "어느 단계였나요?", correction));
            var context = new Context(1L, "작품", "s", List.of(), List.of(), List.of(), turns);
            assertTrue(InterviewQuestions.relatedStatement(context, HUMAN_EDITED_EXPRESSION).isEmpty(), correction);
        }
        // A mixed first answer keeps its positive clause.
        assertEquals("색은 직접 수정했다", statement("색은 직접 수정했고 표정은 수정하지 않았어요.").orElseThrow());
    }

    @Test void noneToAnOpeningLeavesTheEditForReviewInsteadOfDenyingIt() {
        var client = new MockAiInterviewClient();
        String opening = client.generateQuestion(after(5), HUMAN_EDITED_EXPRESSION).content();
        var reply = new Answer(99L, HUMAN_EDITED_EXPRESSION, "없음", opening);
        var context = after(5);
        var withReply = new Context(13L, "사슬과 푸른 시계", "snapshot",
                java.util.stream.Stream.concat(context.answers().stream(), java.util.stream.Stream.of(reply)).toList(),
                List.of(), context.questions(), context.turns());
        var state = client.analyzeAnswer(withReply, reply);
        assertEquals(com.line4.idea2ip.domain.interview.entity.InterviewFieldState.Status.NEEDS_REVIEW, state.status());
        assertFalse(state.closed());
        assertEquals("앞선 답변: 색상과 표정은 직접 수정했어요 / 없음", state.summary());
    }

    private static Optional<InterviewQuestions.Related> related(String outputAnswer) {
        var turns = List.of(new PreviousTurn(AI_OUTPUT_USAGE, "어떻게 활용했나요?", outputAnswer));
        return InterviewQuestions.related(new Context(1L, "작품", "s", List.of(), List.of(), List.of(), turns),
                HUMAN_EDITED_EXPRESSION);
    }

    private static Optional<String> statement(String outputAnswer) {
        var turns = List.of(new PreviousTurn(AI_OUTPUT_USAGE, "어떻게 활용했나요?", outputAnswer));
        return InterviewQuestions.relatedStatement(new Context(1L, "작품", "s", List.of(), List.of(), List.of(), turns),
                HUMAN_EDITED_EXPRESSION);
    }
}
