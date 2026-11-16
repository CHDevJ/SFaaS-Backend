package com.sfaasproject.stf.telegram.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisTelegramLinkTokenStoreTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final TelegramLinkTokenService tokens = new TelegramLinkTokenService();
    private final RedisTelegramLinkTokenStore store = new RedisTelegramLinkTokenStore(redis, tokens);

    @Test
    void savesHashWithTenMinuteTtlAndConsumesAtomically() {
        Instant now = Instant.parse("2026-11-17T00:00:00Z");
        var issued = tokens.issue(now);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), eq("42"), eq(Duration.ofMinutes(10))))
                .thenReturn(true);
        store.save(42, issued, now);
        String key = "telegram:link:" + issued.sha256Hash();
        verify(values).setIfAbsent(key, "42", Duration.ofMinutes(10));
        verify(values, never()).setIfAbsent(contains(issued.rawToken()), anyString(), any(Duration.class));
        when(values.getAndDelete(key)).thenReturn("42", (String) null);
        assertEquals(42L, store.consume(issued.rawToken()));
        assertNull(store.consume(issued.rawToken()));
    }

    @Test
    void expiredTokenCannotBeStored() {
        Instant now = Instant.parse("2026-11-17T00:00:00Z");
        assertThrows(IllegalArgumentException.class,
                () -> store.save(1, tokens.issue(now), now.plus(Duration.ofMinutes(11))));
        verifyNoInteractions(redis);
    }
}
