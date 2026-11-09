package com.line4.idea2ip.domain.user.controller;

import com.line4.idea2ip.domain.user.dto.UserInfoRes;
import com.line4.idea2ip.domain.user.service.UserService;
import com.line4.idea2ip.global.jwt.CustomUserDetails;
import com.line4.idea2ip.global.response.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<SuccessResponse<UserInfoRes>> getMyInfo(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        UserInfoRes response = userService.getMyInfo(userDetails.getUserId());
        return ResponseEntity.ok(SuccessResponse.from(response));
    }
}
