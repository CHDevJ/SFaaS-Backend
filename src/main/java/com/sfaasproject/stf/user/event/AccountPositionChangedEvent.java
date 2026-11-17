package com.sfaasproject.stf.user.event;

/** Published synchronously inside the account update transaction. */
public record AccountPositionChangedEvent(
        long accountId, String previousPosition, String position, long changedBy) {}
