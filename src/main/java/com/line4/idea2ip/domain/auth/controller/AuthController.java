package com.line4.idea2ip.domain.auth.controller;

import com.line4.idea2ip.domain.user.dto.SignupReq;
import com.line4.idea2ip.domain.user.dto.SignupRes;
import com.line4.idea2ip.domain.user.service.UserService;
import com.line4.idea2ip.global.response.SuccessResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/signup")
    public ResponseEntity<SuccessResponse<SignupRes>> signup(@Valid @RequestBody SignupReq req) {
        SignupRes response = userService.signup(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(SuccessResponse.created(response));
    }
}
