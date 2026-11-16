package com.sfaasproject.stf.measurement.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

class RedisIngestionMessageGuardTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final RedisIngestionMessageGuard guard = new RedisIngestionMessageGuard(redis);

    @Test
    void completedClaimUsesAtomicCompareAndSetWithItsOwnToken() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(eq("ingest:message:m1"), startsWith("PROCESSING:"),
                eq(Duration.ofSeconds(60)))).thenReturn(true);
        assertEquals(IngestionMessageGuard.Acquisition.ACQUIRED, guard.acquire("m1"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<RedisScript<Long>> script = ArgumentCaptor.forClass(RedisScript.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> claim = ArgumentCaptor.forClass(String.class);
        guard.completed("m1");
        verify(redis).execute(script.capture(), keys.capture(), claim.capture());
        assertEquals(List.of("ingest:message:m1"), keys.getValue());
        assertTrue(claim.getValue().startsWith("PROCESSING:"));
        assertTrue(script.getValue().getScriptAsString().contains("ARGV[1]"));
        assertTrue(script.getValue().getScriptAsString().contains("'DONE'"));
        guard.release("m1");
    }

    @Test
    void alreadyCompletedMessageNeverGetsClaim() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false);
        when(values.get("ingest:message:m2")).thenReturn("DONE");
        assertEquals(IngestionMessageGuard.Acquisition.COMPLETED, guard.acquire("m2"));
        guard.release("m2");
        verify(redis, never()).execute(any(), anyList(), any());
    }
}
