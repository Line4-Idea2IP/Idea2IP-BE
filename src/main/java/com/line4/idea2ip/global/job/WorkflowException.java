package com.line4.idea2ip.global.job;

import com.line4.idea2ip.global.response.code.BaseResponseCode;

public class WorkflowException extends RuntimeException implements BaseResponseCode {
    private final String code;
    private final int httpStatus;
    private final Long activeVersionId;

    public WorkflowException(String code, int httpStatus, String message) {
        this(code, httpStatus, message, null);
    }

    public WorkflowException(String code, int httpStatus, String message, Long activeVersionId) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.activeVersionId = activeVersionId;
    }

    public String getCode() { return code; }
    public int getHttpStatus() { return httpStatus; }
    public Long getActiveVersionId() { return activeVersionId; }
}
