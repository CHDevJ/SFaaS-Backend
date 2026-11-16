package com.sfaasproject.stf.telegram.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TelegramDeliveryAndTokenTest {
    @Test
    void retriesOnceAfterOneSecondWithoutPropagatingProviderFailure() {
        var attempts = new AtomicInteger();
        var delays = new ArrayList<Duration>();
        var service = new TelegramDeliveryService((chatId, message) -> {
            if (attempts.incrementAndGet() == 1) throw new IllegalStateException("secret token in URL");
        }, delays::add);
        var result = service.sendWithOneRetry("123", "알람");
        assertTrue(result.successful());
        assertEquals(1, result.retryCount());
        assertEquals(List.of(Duration.ofSeconds(1)), delays);
        assertEquals(2, attempts.get());
    }

    @Test
    void secondFailureReturnsSafeReasonWithoutLeakingProviderMessage() {
        var service = new TelegramDeliveryService((chatId, message) -> {
            throw new IllegalStateException("secret token in URL");
        }, duration -> { });
        var result = service.sendWithOneRetry("123", "알람");
        assertFalse(result.successful());
        assertEquals(1, result.retryCount());
        assertFalse(result.failureReason().contains("secret token"));
    }

    @Test
    void linkTokenHasTenMinuteExpiryAndStoresHashInsteadOfRawToken() {
        var service = new TelegramLinkTokenService();
        Instant now = Instant.parse("2026-11-17T00:00:00Z");
        var issued = service.issue(now);
        assertEquals(43, issued.rawToken().length());
        assertEquals(now.plus(Duration.ofMinutes(10)), issued.expiresAt());
        assertFalse(issued.rawToken().equals(issued.sha256Hash()));
        assertTrue(service.matches(issued.rawToken(), issued.sha256Hash()));
        assertFalse(service.matches("wrong", issued.sha256Hash()));
    }
}
