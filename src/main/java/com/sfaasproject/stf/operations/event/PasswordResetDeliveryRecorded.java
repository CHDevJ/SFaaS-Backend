package com.sfaasproject.stf.operations.event;

import java.time.OffsetDateTime;

/** Safe delivery metadata only. Never add chat IDs, provider responses or password-bearing text. */
public record PasswordResetDeliveryRecorded(
        String eventId, long accountId, String receiverName, boolean successful,
        int retryCount, OffsetDateTime attemptedAt) {}
