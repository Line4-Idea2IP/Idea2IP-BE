package com.line4.idea2ip.global.response;

import com.line4.idea2ip.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Getter
@ToString
@RequiredArgsConstructor
public class BaseResponse {
    private final Boolean isSuccess;
    private final String code;
    private final String message;
    private final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

    public static BaseResponse of(Boolean isSuccess, BaseResponseCode baseResponseCode){
        return new BaseResponse(isSuccess, baseResponseCode.getCode(), baseResponseCode.getMessage());
    }
    public static BaseResponse of(Boolean isSuccess, BaseResponseCode baseResponseCode, String message){
        return new BaseResponse(isSuccess, baseResponseCode.getCode(), message);
    }
    public static BaseResponse of(Boolean isSuccess, String code, String message){
        return new BaseResponse(isSuccess, code, message);
    }
}
