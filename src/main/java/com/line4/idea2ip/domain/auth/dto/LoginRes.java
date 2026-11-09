package com.line4.idea2ip.domain.auth.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginRes {
    private String accessToken;
}
