package com.line4.idea2ip.global.job;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Getter
@Embeddable
public class PersistentJob {
    public enum Status { QUEUED, RUNNING, SUCCEEDED, FAILED }

    @Enumerated(EnumType.STRING)
    @Column(name = "job_status", nullable = false, length = 12)
    private Status status = Status.QUEUED;

    private Instant leaseUntil;
    private UUID leaseToken;
    private String failureCode;
    private boolean retryable;

    public boolean active() {
        return status == Status.QUEUED || status == Status.RUNNING;
    }

    public UUID claim(Instant now) {
        if (status != Status.QUEUED && !(status == Status.RUNNING && !leaseUntil.isAfter(now))) {
            return null;
        }
        status = Status.RUNNING;
        leaseToken = UUID.randomUUID();
        leaseUntil = now.plus(5, ChronoUnit.MINUTES);
        return leaseToken;
    }

    public boolean owns(UUID token) {
        return status == Status.RUNNING && token.equals(leaseToken) && leaseUntil.isAfter(Instant.now());
    }

    public void succeed() {
        status = Status.SUCCEEDED;
        clearLease();
    }

    public void fail(String code, boolean retryable) {
        status = Status.FAILED;
        failureCode = code;
        this.retryable = retryable;
        clearLease();
    }

    public void retry() {
        if (status == Status.FAILED && retryable) {
            status = Status.QUEUED;
            failureCode = null;
            retryable = false;
        }
    }

    private void clearLease() {
        leaseUntil = null;
        leaseToken = null;
    }

    public Failure failure() {
        return failureCode == null ? null : new Failure(failureCode, switch (failureCode) {
            case "AI_UNAVAILABLE" -> "AI 처리에 일시적으로 실패했습니다.";
            case "AI_RESULT_INVALID" -> "AI 결과의 형식 또는 출처를 확인할 수 없습니다.";
            case "AI_INPUT_INVALID" -> "AI 요청에 사용할 입력을 확인해 주세요.";
            case "PDF_RENDER_FAILED" -> "PDF 파일을 생성하지 못했습니다.";
            case "STALE_BASE_VERSION" -> "기준 리포트 버전이 변경되었습니다.";
            default -> "작업을 완료하지 못했습니다.";
        }, retryable);
    }

    public record Failure(String code, String message, boolean retryable) {}
}
