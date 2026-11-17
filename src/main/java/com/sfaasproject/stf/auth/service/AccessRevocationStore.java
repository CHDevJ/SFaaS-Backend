package com.sfaasproject.stf.auth.service;

import java.time.Instant;

/**
 * 폐기된 Access Token 을 보관한다. Access Token 은 최대 30분만 유효하므로 그 시간 동안만 기억하면 된다.
 * 구현이 오류를 던지면 호출하는 쪽은 토큰을 폐기된 것으로 보고 거부해야 한다(fail-closed).
 */
public interface AccessRevocationStore {

    /** 로그아웃한 Access Token(jti)을 토큰 만료 시각까지 폐기 상태로 둔다. */
    void revokeAccess(String jti, Instant tokenExpiresAt);

    boolean isAccessRevoked(String jti);

    /** 이 사용자에게 {@code cutoff} 이전(같은 초 포함)에 발급된 모든 Access Token 을 폐기한다. */
    void revokeUserTokensIssuedUntil(long userId, Instant cutoff);

    boolean isIssuedAtOrBeforeCutoff(long userId, Instant issuedAt);
}
