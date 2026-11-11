package com.line4.idea2ip.domain.auth.repository;

import com.line4.idea2ip.domain.auth.entity.RevokedAccessToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface RevokedAccessTokenRepository extends JpaRepository<RevokedAccessToken, String> {

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO revoked_access_tokens (token_id, expires_at)
            VALUES (:tokenId, :expiresAt)
            ON CONFLICT (token_id) DO NOTHING
            """, nativeQuery = true)
    void revoke(@Param("tokenId") String tokenId, @Param("expiresAt") Instant expiresAt);

    @Modifying
    @Transactional
    @Query("delete from RevokedAccessToken token where token.expiresAt < :now")
    void deleteExpired(@Param("now") Instant now);
}
