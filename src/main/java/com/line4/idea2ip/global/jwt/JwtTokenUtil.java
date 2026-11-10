package com.line4.idea2ip.global.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

@Component
@RequiredArgsConstructor
public class JwtTokenUtil {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtParser jwtParser;

    public String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        return header.substring(BEARER_PREFIX.length()).trim();
    }

    public CustomUserDetails getUserDetails(String token) {
        Jws<Claims> signedClaims = jwtParser.parseSignedClaims(token);
        Claims claims = signedClaims.getPayload();
        if (claims.getExpiration() == null || !claims.getExpiration().toInstant().isAfter(Instant.now())) {
            throw new MalformedJwtException("A future access token expiration is required");
        }

        String tokenId = claims.getId();
        if (tokenId == null) {
            tokenId = legacyTokenId(signedClaims.getDigest());
        }
        return new CustomUserDetails(Long.valueOf(claims.getSubject()), tokenId, claims.getExpiration().toInstant());
    }

    private String legacyTokenId(byte[] signature) {
        try {
            // 기존 JWT는 검증된 서명 바이트로 같은 토큰을 식별한다.
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(signature);
            return "legacy:" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
