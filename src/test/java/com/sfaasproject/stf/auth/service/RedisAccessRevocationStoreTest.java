package com.sfaasproject.stf.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisAccessRevocationStoreTest {

    private static final Instant NOW = Instant.parse("2026-11-17T01:00:00Z");

    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private RedisAccessRevocationStore store;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        store = new RedisAccessRevocationStore(redis, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void a_revoked_jti_is_kept_only_for_the_remaining_lifetime_of_the_token() {
        store.revokeAccess("jti-1", NOW.plus(Duration.ofMinutes(20)));

        verify(values).set("auth:access:revoked:jti-1", "1", Duration.ofMinutes(20));
    }

    @Test
    void an_already_expired_token_is_not_stored() {
        store.revokeAccess("jti-1", NOW.minusSeconds(1));
        store.revokeAccess("jti-2", NOW);

        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void revocation_is_looked_up_by_jti() {
        when(redis.hasKey("auth:access:revoked:jti-1")).thenReturn(true);

        assertThat(store.isAccessRevoked("jti-1")).isTrue();
        assertThat(store.isAccessRevoked("jti-2")).isFalse();
    }

    @Test
    void a_user_cutoff_is_kept_for_longer_than_an_access_token_lives() {
        store.revokeUserTokensIssuedUntil(7, NOW);

        verify(redis).execute(any(org.springframework.data.redis.core.script.RedisScript.class),
                eq(java.util.List.of("auth:user:revoked-at:v7:7")),eq("2026-11-17T01:00:00.000000000Z"),eq("1860"));
    }

    @Test
    void tokens_issued_up_to_the_cutoff_second_are_revoked_and_later_ones_are_not() {
        when(values.get("auth:user:revoked-until:7")).thenReturn(Long.toString(NOW.getEpochSecond()));

        assertThat(store.isIssuedAtOrBeforeCutoff(7, NOW.minusSeconds(10))).isTrue();
        assertThat(store.isIssuedAtOrBeforeCutoff(7, NOW)).isTrue();
        assertThat(store.isIssuedAtOrBeforeCutoff(7, NOW.plusSeconds(1))).isFalse();
        assertThat(store.isIssuedAtOrBeforeCutoff(8, NOW.minusSeconds(10))).as("다른 사용자").isFalse();
    }

    @Test
    void invalid_arguments_are_rejected() {
        assertThatThrownBy(() -> store.revokeAccess(" ", NOW.plusSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.revokeUserTokensIssuedUntil(7, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
