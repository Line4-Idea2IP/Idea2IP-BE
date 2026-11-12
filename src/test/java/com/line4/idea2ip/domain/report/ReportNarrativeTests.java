package com.line4.idea2ip.domain.report;

import com.line4.idea2ip.domain.contribution.ContributionModels.AiUsage;
import com.line4.idea2ip.domain.contribution.ContributionModels.Confirmed;
import com.line4.idea2ip.domain.contribution.ContributionModels.Content;
import com.line4.idea2ip.domain.report.ReportModels.InputSnapshot;
import com.line4.idea2ip.domain.report.ReportModels.MaterialFact;
import com.line4.idea2ip.domain.report.ReportModels.Profile;
import com.line4.idea2ip.integration.ai.BriefAiClient.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReportNarrativeTests {
    private static Evidence ev(String id, String kind, String title, String quote) {
        return new Evidence(id, kind, title, quote, null, null, null, null, null, null, null, null);
    }

    private final Source artwork = new Source("artwork:3", "ATTACHED_FILE", "사슬과 푸른 시계", "사슬과 푸른 시계\n완성작",
            null, "upload.png", null, 3L, null, null);
    private final Evidence ai = ev("material:5", "ATTACHED_FILE", "구도 자료", null);
    private final Evidence edit = ev("material:6", "ATTACHED_FILE", "색 대비 자료", null);
    private final Evidence tool = ev("answer:22", "USER_STATEMENT", "인터뷰 답변 2 (AI_TOOL)", "Stable Diffusion");
    private final Evidence purpose = ev("answer:24", "USER_STATEMENT", "인터뷰 답변 4 (AI_PURPOSE)", "손의 배치 시안 비교");
    private final Evidence painted = ev("answer:30", "USER_STATEMENT", "인터뷰 답변 10 (HUMAN_EDITED_EXPRESSION)", "시계판을 칠했습니다.");
    private final List<Confirmed> steps = List.of(
            new Confirmed("구도 자료", new Content(new AiUsage(List.of("Stable Diffusion"), "손의 배치 시안 비교", null),
                    List.of(), List.of()), List.of(ai, tool, purpose)),
            new Confirmed("색 대비 자료", new Content(null, List.of(), List.of("시계판을 칠했습니다.")), List.of(edit, painted)));
    private final Profile profile = new Profile("CHARACTER_DESIGN", null, "사슬과 푸른 시계", "완성작", List.of(
            new MaterialFact(6L, "MANUAL_EDIT", "색 대비 자료", "시계 색을 비교함", null, "2026-11-03", 1, 2),
            new MaterialFact(5L, "AI_ASSISTED", "구도 자료", "손 배치 시안 4장", "Stable Diffusion", "2026-11-01", 2, 1),
            new MaterialFact(7L, "INITIAL_SKETCH", "스케치", null, null, null, 0, 0)));

    private List<Section> sections(Profile profile) {
        var input = new InputSnapshot(new Input("3", "사슬과 푸른 시계", "v1", List.of(artwork)), steps, null, List.of(), profile);
        return new ReportContentAssembler().assemble(input, new Draft("1.0", "live", List.of(), List.of()), null, null).sections();
    }

    @Test void overviewSeparatesWorkFactsAndMarksMissingOnesUnverified() {
        assertEquals(List.of("작품명: 사슬과 푸른 시계", "작품 유형: 캐릭터 디자인", "주제·기획 의도: 미확인",
                "최종 작품: 사슬과 푸른 시계", "최종 작품 설명: 완성작"), sections(profile).get(0).paragraphs());
        assertEquals(List.of("artwork:3"), sections(profile).get(0).sourceIds());
    }

    @Test void aiUsageLinksStageOutputAndFinalUse() {
        assertEquals(List.of("구도 자료 (AI 산출물 · 2026-11-01)\n도구: Stable Diffusion\n산출물 설명: 손 배치 시안 4장"
                + "\n사용 목적: 손의 배치 시안 비교\n최종 작품 반영: 미확인"), sections(profile).get(1).paragraphs());
    }

    @Test void humanContributionNamesTheMaterialAndStage() {
        assertEquals(List.of("색 대비 자료 (직접 수정본 · 2026-11-03)\n직접 수정: 시계판을 칠했습니다."),
                sections(profile).get(2).paragraphs());
    }

    @Test void referencesFollowProductionOrderWithLinkedWork() {
        var references = sections(profile).get(3).paragraphs();
        assertEquals("1. [AI 산출물] 구도 자료\n제작일: 2026-11-01 · 첨부 파일 2개\n설명: 손 배치 시안 4장"
                + "\n사용 도구: Stable Diffusion\n연결된 작업: AI 활용", references.get(0));
        assertEquals("2. [직접 수정본] 색 대비 자료\n제작일: 2026-11-03 · 첨부 파일 1개\n설명: 시계 색을 비교함"
                + "\n연결된 작업: 직접 수정 1건", references.get(1));
        assertEquals("3. [초기 스케치] 스케치\n제작일: 미입력 · 첨부 파일 0개", references.get(2));
        assertEquals("4. [최종 작품] 사슬과 푸른 시계", references.get(3));
        assertEquals("인터뷰 답변 3건 (사용자 진술) — 아래 출처 세부 정보 참고", references.get(4));
    }

    @Test void snapshotsWithoutProfileKeepTheEarlierLayout() {
        var old = sections(null);
        assertEquals(List.of("사슬과 푸른 시계\n완성작"), old.get(0).paragraphs());
        assertTrue(old.get(3).paragraphs().contains("첨부자료 · 구도 자료"));
    }
}
