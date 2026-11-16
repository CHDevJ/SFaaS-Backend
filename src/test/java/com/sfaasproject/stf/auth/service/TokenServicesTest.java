package com.sfaasproject.stf.auth.service;

import static org.junit.jupiter.api.Assertions.*;

import com.sfaasproject.stf.auth.service.AuthPolicyService.Position;
import com.sfaasproject.stf.common.DomainException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class TokenServicesTest {
    private static final byte[] SECRET =
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    private final Instant now = Instant.parse("2026-11-17T00:00:00Z");

    @Test
    void jwtHasThirtyMinuteExpiryAndRejectsTamperingAndExpiry() {
        var service = new JwtAccessTokenService(SECRET, "stf");
        var issued = service.issue(7, Position.MAINTENANCE, now);
        var claims = service.verify(issued.value(), now);
        assertEquals(7, claims.userId());
        assertEquals(Position.MAINTENANCE, claims.position());
        assertEquals(issued.jti(), claims.jti());
        assertEquals(now.plus(30, ChronoUnit.MINUTES), issued.expiresAt());
        assertEquals("Issued[redacted]", issued.toString());
        assertThrows(DomainException.class,
                () -> service.verify(issued.value() + "x", now));
        assertThrows(DomainException.class,
                () -> service.verify(issued.value(), issued.expiresAt()));
        assertThrows(DomainException.class,
                () -> new JwtAccessTokenService(SECRET, "other").verify(issued.value(), now));
    }

    @Test
    void rejectsWeakSigningSecret() {
        assertThrows(IllegalArgumentException.class,
                () -> new JwtAccessTokenService("short".getBytes(StandardCharsets.UTF_8), "stf"));
    }

    @Test
    void refreshTokenIsRandomHashedAndExpiresAfterSevenDays() {
        var service = new RefreshTokenService();
        var first = service.issue(now);
        var second = service.issue(now);
        assertNotEquals(first.value(), second.value());
        assertNotEquals(first.value(), first.hash());
        assertEquals(64, first.hash().length());
        assertEquals(now.plus(7, ChronoUnit.DAYS), first.expiresAt());
        assertEquals(first.hash(), service.hash(first.value()));
        assertThrows(IllegalArgumentException.class, () -> service.hash(""));
    }
}
