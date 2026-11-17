package com.sfaasproject.stf.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * ERD A2 auth_token. Refresh Token 의 SHA-256 값만 저장한다(원문은 저장하지 않는다).
 * 로그인 한 번이 한 계열(family)이고, 회전할 때마다 같은 계열에 새 줄이 쌓인다.
 */
@Entity
@Table(name = "auth_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "token_id")
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "family_id", length = 36, nullable = false)
    private String familyId;

    @Column(name = "token_hash", length = 64, nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** 새 토큰으로 교체한 시각. 채워진 토큰이 다시 오면 재사용이다. */
    @Column(name = "used_at")
    private LocalDateTime usedAt;

    /** 폐기 시각. 로그아웃이나 재사용 감지로 계열을 폐기하면 채운다. */
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    public AuthTokenEntity(Long accountId, String familyId, String tokenHash, LocalDateTime issuedAt,
            LocalDateTime expiresAt) {
        this.accountId = accountId;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public void markUsed(LocalDateTime at) {
        this.usedAt = at;
    }
}
