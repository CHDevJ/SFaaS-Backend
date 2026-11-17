package com.sfaasproject.stf.operations.service;

import com.sfaasproject.stf.auth.service.AuthFlowService.SessionStore;
import com.sfaasproject.stf.auth.service.JwtAccessTokenService.Claims;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;

/** Prefer A's session port; fallback uses the documented shared Redis revocation keys. */
public class BTokenGuard {
    private final StringRedisTemplate redis;
    private final ObjectProvider<SessionStore> sessions;

    public BTokenGuard(StringRedisTemplate redis, ObjectProvider<SessionStore> sessions) {
        this.redis = redis;
        this.sessions = sessions;
    }

    public boolean revoked(Claims claims) {
        var store = sessions.getIfAvailable();
        if (store != null)
            return store.isAccessRevoked(claims.jti(), claims.userId(), claims.issuedAt());
        if (Boolean.TRUE.equals(redis.hasKey("auth:access:revoked:" + claims.jti()))) return true;
        String before = redis.opsForValue().get("auth:user:revoked-before:" + claims.userId());
        return before != null
                && !claims.issuedAt().isAfter(Instant.ofEpochMilli(Long.parseLong(before)));
    }
}
