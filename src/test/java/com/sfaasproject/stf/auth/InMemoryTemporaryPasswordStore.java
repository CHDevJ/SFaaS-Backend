package com.sfaasproject.stf.auth;

import com.sfaasproject.stf.auth.service.TemporaryPasswordStore;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 테스트에서 Redis 없이 임시 비밀번호 만료를 다루기 위한 메모리 구현. */
public class InMemoryTemporaryPasswordStore implements TemporaryPasswordStore {

    private final Map<Long, Instant> expiries = new ConcurrentHashMap<>();

    @Override
    public void issued(long userId, Instant expiresAt) {
        expiries.put(userId, expiresAt);
    }

    @Override
    public boolean isExpired(long userId, Instant now) {
        Instant expiresAt = expiries.get(userId);
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    @Override
    public void clear(long userId) {
        expiries.remove(userId);
    }

    public boolean has(long userId) {
        return expiries.containsKey(userId);
    }

    /** 테스트에서 30분이 지난 것처럼 만든다. */
    public void expire(long userId) {
        expiries.put(userId, Instant.EPOCH);
    }

    public void reset() {
        expiries.clear();
    }
}
