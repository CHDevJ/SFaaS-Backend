package com.sfaasproject.stf.auth.service;

import static org.junit.jupiter.api.Assertions.*;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.*;
import com.sfaasproject.stf.auth.InMemoryAccessRevocationStore;
import com.sfaasproject.stf.auth.service.AuthPolicyService.Permission;
import com.sfaasproject.stf.common.DomainException;

import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.Date;

class V7TokenPrecisionTest {
    final byte[] key =
            "v7-unit-test-only-secret-0123456789-abcdef"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    final JwtAccessTokenService jwt = new JwtAccessTokenService(key, "stf");
    final Instant second = Instant.parse("2026-11-17T01:00:00Z");

    @Test
    void sameSecondReloginSurvivesCutoffButTheEarlierTokenDoesNot() {
        var old =
                jwt.verify(
                        jwt.issue(1, Permission.USER, second.plusMillis(100)).value(),
                        second.plusMillis(900));
        var fresh =
                jwt.verify(
                        jwt.issue(1, Permission.ADMIN, second.plusMillis(700)).value(),
                        second.plusMillis(900));
        var store = new InMemoryAccessRevocationStore();
        store.revokeUserTokensIssuedUntil(1, second.plusMillis(500));
        assertEquals(second.plusMillis(100), old.issuedAt());
        assertEquals(second.plusMillis(700), fresh.issuedAt());
        assertTrue(store.isIssuedAtOrBeforeCutoff(1, old.issuedAt()));
        assertFalse(store.isIssuedAtOrBeforeCutoff(1, fresh.issuedAt()));
    }

    @Test
    void olderConcurrentCutoffCannotRestoreRevokedTokens() {
        var store = new InMemoryAccessRevocationStore();
        store.revokeUserTokensIssuedUntil(1, second.plusMillis(700));
        store.revokeUserTokensIssuedUntil(1, second.plusMillis(100));
        assertTrue(store.isIssuedAtOrBeforeCutoff(1, second.plusMillis(500)));
    }

    @Test
    void oldPositionClaimAndMalformedExactTimestampAre401() throws Exception {
        for (String exact : new String[] {"invalid", second.plusSeconds(1).toString()}) {
            var claims =
                    new JWTClaimsSet.Builder()
                            .issuer("stf")
                            .subject("1")
                            .jwtID("test-jti")
                            .issueTime(Date.from(second))
                            .expirationTime(Date.from(second.plusSeconds(1800)))
                            .claim("permission", "USER")
                            .claim("issuedAt", exact)
                            .build();
            assertEquals(
                    401,
                    assertThrows(
                                    DomainException.class,
                                    () -> jwt.verify(sign(claims), second.plusSeconds(2)))
                            .status()
                            .value());
        }
        var legacy =
                new JWTClaimsSet.Builder()
                        .issuer("stf")
                        .subject("1")
                        .jwtID("test-jti")
                        .issueTime(Date.from(second))
                        .expirationTime(Date.from(second.plusSeconds(1800)))
                        .claim("position", "SYSTEM_ADMIN")
                        .build();
        assertEquals(
                401,
                assertThrows(
                                DomainException.class,
                                () -> jwt.verify(sign(legacy), second.plusSeconds(2)))
                        .status()
                        .value());
    }

    String sign(JWTClaimsSet claims) throws Exception {
        var token = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        token.sign(new MACSigner(key));
        return token.serialize();
    }
}
