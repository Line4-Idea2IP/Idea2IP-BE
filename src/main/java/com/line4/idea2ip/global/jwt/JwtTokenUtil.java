package com.line4.idea2ip.global.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

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

    public Long getUserId(String token) {
        Claims claims = jwtParser.parseSignedClaims(token).getPayload();
        if (claims.getExpiration() == null) {
            throw new MalformedJwtException("Access token expiration is required");
        }
        return Long.valueOf(claims.getSubject());
    }
}
