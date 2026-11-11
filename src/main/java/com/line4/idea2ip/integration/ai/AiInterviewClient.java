package com.line4.idea2ip.integration.ai;

import com.line4.idea2ip.domain.interview.entity.InterviewField;
import com.line4.idea2ip.domain.interview.entity.InterviewFieldState;
import com.line4.idea2ip.domain.material.entity.MaterialType;

import java.util.List;

public interface AiInterviewClient {
    QuestionDraft generateQuestion(Context context, InterviewField field);

    InterviewFieldState analyzeAnswer(Context context, Answer answer);

    record Context(Long projectId, String title, String snapshotVersion,
                   List<Answer> answers, List<Evidence> evidence) {
    }

    record Answer(Long messageId, InterviewField field, String content) {
    }

    record Evidence(String sourceId, MaterialType type, String title, String description, String usedAi) {
    }

    record QuestionDraft(String content, String reason, List<String> sourceIds) {
    }
}
