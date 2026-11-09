package com.line4.idea2ip.domain.user.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SignupRes {
    private Long userId;
    private String loginId;
}
