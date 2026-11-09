package com.line4.idea2ip.global.response.code;

import lombok.AllArgsConstructor;
import lombok.Getter;

import static com.line4.idea2ip.global.constant.StaticValue.*;

@Getter
@AllArgsConstructor
public enum ErrorResponseCode implements BaseResponseCode {
    BAD_REQUEST_ERROR("GLOBAL_400_1", 400, "잘못된 요청입니다."),
    INVALID_HTTP_MESSAGE_BODY("GLOBAL_400_2", BAD_REQUEST, "HTTP 요청 바디의 형식이 잘못되었습니다."),
    INVALID_HTTP_MESSAGE_PARAMETER("GLOBAL_400_3", 400, "HTTP 요청 파라미터 형식이 절못되었습니다."),
    UNAUTHORIZED_REQUEST("GLOBAL_401", UNAUTHORIZED, "인증이 필요합니다."),
    INVALID_LOGIN_CREDENTIALS("AUTH_401", UNAUTHORIZED, "로그인 아이디 또는 비밀번호가 일치하지 않습니다."),
    ACCESS_DENIED_REQUEST("GLOBAL_403", FORBIDDEN, "해당 요청에 접근 권한이 없습니다."),
    NOT_FOUND_ENDPOINT("GLOBAL_404", NOT_FOUND, "존재하지 않는 엔드포인트입니다. 요청 URL을 확인해주세요."),
    UNSUPPORTED_HTTP_METHOD("GLOBAL_405", METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메소드입니다."),
    PASSWORD_TOO_LONG("USER_400", BAD_REQUEST, "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다."),
    DUPLICATE_LOGIN_ID("USER_409", CONFLICT, "이미 사용 중인 로그인 아이디입니다."),
    SERVER_ERROR("GLOBAL_500", INTERNAL_SERVER_ERROR, "서버 내부에서 알 수 없는 에러가 발생했습니다.");

    private final String code;
    private final int httpStatus;
    private final String message;
}
