package com.line4.idea2ip.domain.user.service;

import com.line4.idea2ip.domain.user.dto.SignupReq;
import com.line4.idea2ip.domain.user.dto.SignupRes;
import com.line4.idea2ip.domain.user.entity.User;
import com.line4.idea2ip.domain.user.repository.UserRepository;
import com.line4.idea2ip.global.exception.BaseException;
import com.line4.idea2ip.global.response.code.ErrorResponseCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;
    private static final int MYSQL_DUPLICATE_ENTRY = 1062;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public SignupRes signup(SignupReq req) {
        String loginId = req.getLoginId();
        String password = req.getPassword();

        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
            throw new BaseException(ErrorResponseCode.PASSWORD_TOO_LONG);
        }
        if (userRepository.existsByLoginId(loginId)) {
            throw new BaseException(ErrorResponseCode.DUPLICATE_LOGIN_ID);
        }

        User user = User.builder()
                .loginId(loginId)
                .passwordHash(passwordEncoder.encode(password))
                .build();

        User savedUser;
        try {
            savedUser = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            if (e.getMostSpecificCause() instanceof SQLException sqlException
                    && sqlException.getErrorCode() == MYSQL_DUPLICATE_ENTRY) {
                throw new BaseException(ErrorResponseCode.DUPLICATE_LOGIN_ID);
            }
            throw e;
        }

        return SignupRes.builder()
                .userId(savedUser.getUserId())
                .loginId(savedUser.getLoginId())
                .build();
    }
}
