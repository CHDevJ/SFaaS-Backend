package com.sfaasproject.stf.operations.event;

/** Internal outbox event; a rejected applicant's DB reference is intentionally absent. */
public record SignupNotificationRequested(String eventId, Long userId, String name,
        String chatId, String message) {
    @Override public String toString() { return "SignupNotificationRequested[redacted]"; }
}
