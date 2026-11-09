package com.line4.idea2ip.global.jwt;

import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long accessExpiration;

    public JwtTokenProvider(SecretKey key, @Value("${jwt.access.expiration}") long accessExpiration) {
        if (accessExpiration <= 0) {
            throw new IllegalArgumentException("JWT access token expiration must be positive");
        }
        this.key = key;
        this.accessExpiration = accessExpiration;
    }

    public String createAccessToken(Long userId) {
        Date now = new Date();
        Date expiry = new Date(Math.addExact(now.getTime(), accessExpiration));
        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }
}
