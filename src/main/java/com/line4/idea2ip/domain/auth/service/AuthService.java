package com.line4.idea2ip.domain.auth.service;

import com.line4.idea2ip.domain.auth.dto.LoginReq;
import com.line4.idea2ip.domain.auth.dto.LoginRes;
import com.line4.idea2ip.domain.auth.repository.RevokedAccessTokenRepository;
import com.line4.idea2ip.domain.user.entity.User;
import com.line4.idea2ip.domain.user.repository.UserRepository;
import com.line4.idea2ip.global.exception.BaseException;
import com.line4.idea2ip.global.jwt.CustomUserDetails;
import com.line4.idea2ip.global.jwt.JwtTokenProvider;
import com.line4.idea2ip.global.response.code.ErrorResponseCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RevokedAccessTokenRepository revokedAccessTokenRepository;

    @Transactional(readOnly = true)
    public LoginRes login(LoginReq req) {
        String password = req.getPassword();
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
            throw new BaseException(ErrorResponseCode.INVALID_LOGIN_CREDENTIALS);
        }

        User user = userRepository.findByLoginId(req.getLoginId())
                .orElseThrow(() -> new BaseException(ErrorResponseCode.INVALID_LOGIN_CREDENTIALS));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BaseException(ErrorResponseCode.INVALID_LOGIN_CREDENTIALS);
        }

        return LoginRes.builder()
                .accessToken(jwtTokenProvider.createAccessToken(user.getUserId()))
                .build();
    }

    public void logout(CustomUserDetails userDetails) {
        revokedAccessTokenRepository.deleteExpired(Instant.now());
        revokedAccessTokenRepository.revoke(userDetails.getTokenId(), userDetails.getExpiresAt());
    }
}
