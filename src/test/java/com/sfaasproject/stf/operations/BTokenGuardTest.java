package com.sfaasproject.stf.operations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sfaasproject.stf.auth.service.AuthFlowService.SessionStore;
import com.sfaasproject.stf.auth.service.AuthPolicyService.Position;
import com.sfaasproject.stf.auth.service.JwtAccessTokenService.Claims;
import com.sfaasproject.stf.operations.service.BTokenGuard;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.*;

import java.time.*;

class BTokenGuardTest {
    final StringRedisTemplate redis = mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    final ObjectProvider<SessionStore> providers = mock(ObjectProvider.class);

    final Claims claims =
            new Claims(
                    1,
                    Position.LINE_MANAGER,
                    "jti",
                    Instant.parse("2026-11-17T00:00:00Z"),
                    Instant.parse("2026-11-17T00:30:00Z"));

    @Test
    void usesAProvidedSessionStoreFirst() {
        var store = mock(SessionStore.class);
        when(providers.getIfAvailable()).thenReturn(store);
        when(store.isAccessRevoked("jti", 1, claims.issuedAt())).thenReturn(true);
        assertTrue(new BTokenGuard(redis, providers).revoked(claims));
        verifyNoInteractions(redis);
    }

    @Test
    void redisDenylistRejectsLoggedOutAccessToken() {
        when(redis.hasKey("auth:access:revoked:jti")).thenReturn(true);
        assertTrue(new BTokenGuard(redis, providers).revoked(claims));
    }

    @Test
    void userCutoffRejectsPrePasswordChangeTokens() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("auth:user:revoked-before:1"))
                .thenReturn(Long.toString(claims.issuedAt().plusMillis(1).toEpochMilli()));
        assertTrue(new BTokenGuard(redis, providers).revoked(claims));
    }
}
