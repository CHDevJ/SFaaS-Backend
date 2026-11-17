package com.sfaasproject.stf.auth;

import com.sfaasproject.stf.auth.service.AccessRevocationStore;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 테스트에서 Redis 없이 토큰 폐기를 검증하기 위한 메모리 구현. */
public class InMemoryAccessRevocationStore implements AccessRevocationStore {

    private final Set<String> revokedJtis = ConcurrentHashMap.newKeySet();
    private final Map<Long, Instant> cutoffs = new ConcurrentHashMap<>();
    private volatile boolean failing;

    @Override
    public void revokeAccess(String jti, Instant tokenExpiresAt) {
        revokedJtis.add(jti);
    }

    @Override
    public boolean isAccessRevoked(String jti) {
        failIfRequested();
        return revokedJtis.contains(jti);
    }

    @Override
    public void revokeUserTokensIssuedUntil(long userId, Instant cutoff) {
        failIfRequested();
        cutoffs.merge(userId, cutoff, (a,b)->a.isAfter(b)?a:b);
    }

    @Override
    public boolean isIssuedAtOrBeforeCutoff(long userId, Instant issuedAt) {
        failIfRequested();
        Instant cutoff = cutoffs.get(userId);
        return cutoff != null && !issuedAt.isAfter(cutoff);
    }

    /** true 이면 모든 조회·기록이 예외를 던진다(Redis 장애 시뮬레이션). */
    public void setFailing(boolean failing) {
        this.failing = failing;
    }

    public void reset() {
        revokedJtis.clear();
        cutoffs.clear();
        failing = false;
    }

    private void failIfRequested() {
        if (failing) {
            throw new IllegalStateException("simulated revocation store failure");
        }
    }
}
