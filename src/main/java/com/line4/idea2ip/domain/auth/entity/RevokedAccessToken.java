package com.line4.idea2ip.domain.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "revoked_access_tokens", indexes = @Index(name = "idx_revoked_token_expiration", columnList = "expires_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RevokedAccessToken {

    @Id
    @Column(name = "token_id", length = 71)
    private String tokenId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
