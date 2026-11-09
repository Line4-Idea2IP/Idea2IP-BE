package com.line4.idea2ip.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SignupReq {

    @NotBlank(message = "로그인 아이디를 입력해주세요.")
    @Size(max = 50, message = "로그인 아이디는 50자 이하여야 합니다.")
    private String loginId;

    @NotBlank(message = "비밀번호를 입력해주세요.")
    private String password;
}
