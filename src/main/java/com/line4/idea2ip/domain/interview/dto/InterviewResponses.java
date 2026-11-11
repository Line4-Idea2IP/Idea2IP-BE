package com.line4.idea2ip.domain.interview.dto;

import com.line4.idea2ip.domain.interview.entity.AiInterview;
import com.line4.idea2ip.domain.interview.entity.InterviewField;
import com.line4.idea2ip.domain.interview.entity.InterviewFieldState;
import com.line4.idea2ip.domain.interview.entity.InterviewMessage;

import java.time.LocalDateTime;
import java.util.List;

public final class InterviewResponses {
    private InterviewResponses() {
    }

    public enum MaterialType {
        FINAL_ARTWORK,
        AI_MATERIAL,
        MANUAL_EDIT_MATERIAL
    }

    public record MaterialCheck(MaterialType type, boolean ready) {
    }

    public record BlockingReason(MaterialType type, String message) {
    }

    public record MaterialWarning(MaterialType type, String message) {
    }

    public record ReadinessRes(boolean canStart, List<InterviewFieldState> fieldStates,
                               List<MaterialCheck> materialChecks, List<BlockingReason> blockingReasons,
                               List<MaterialWarning> warnings) {
    }

    public record InterviewStateRes(Long interviewId, AiInterview.Status status, List<InterviewFieldState> fieldStates,
                                    Question currentQuestion, boolean nextQuestionPending) {
    }

    public record Question(Long messageId, String content, InterviewField targetField,
                           String reason, List<MaterialRef> materialRefs) {
    }

    public record MaterialRef(String sourceId, Long materialId, Long fileId, String title) {
    }

    public record Message(Long messageId, InterviewMessage.Role role, String content, int sequenceNo,
                          InterviewField targetField, Long replyToQuestionId, LocalDateTime createdAt) {
    }

    public record MessagesRes(List<Message> messages) {
    }

    public record AnswerRes(Long answerMessageId, InterviewStateRes interview) {
    }
}
