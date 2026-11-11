package com.line4.idea2ip.domain.interview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record InterviewAnswerReq(
        @NotNull @Positive Long questionId,
        @NotBlank @Size(max = 8000) String content,
        @NotNull UUID requestId
) {
    public InterviewAnswerReq {
        if (content != null) {
            content = content.strip();
        }
    }
}
