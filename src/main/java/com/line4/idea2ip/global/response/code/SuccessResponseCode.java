package com.line4.idea2ip.global.response.code;

import lombok.AllArgsConstructor;
import lombok.Getter;

import static com.line4.idea2ip.global.constant.StaticValue.*;

@Getter
@AllArgsConstructor
public enum SuccessResponseCode implements BaseResponseCode{
    SUCCESS_OK("GLOBAL_200",OK,"호출에 성공하였습니다."),
    SUCCESS_CREATED("GLOBAL_201",CREATED,"생성에 성공하였습니다.");

    private final String code;
    private final int httpStatus;
    private final String message;
}