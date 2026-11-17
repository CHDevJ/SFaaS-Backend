package com.sfaasproject.stf.auth.service;

import com.sfaasproject.stf.auth.entity.AuthTokenEntity;
import com.sfaasproject.stf.auth.repository.AuthTokenRepository;
import com.sfaasproject.stf.common.time.KstTime;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link AuthFlowService.SessionStore} 구현. Refresh Token 은 DB(auth_token)에, 폐기된 Access Token 은
 * {@link AccessRevocationStore}(Redis)에 둔다.
 *
 * <p>회전은 한 트랜잭션 안에서 줄을 잠그고 읽어 처리하므로 같은 토큰으로 동시에 요청해도 하나만 성공한다.
 */
@Component
@Transactional
public class DatabaseSessionStore implements AuthFlowService.SessionStore {

    private final AuthTokenRepository tokens;
    private final AccessRevocationStore accessRevocations;
    private final Clock clock;

    public DatabaseSessionStore(AuthTokenRepository tokens, AccessRevocationStore accessRevocations,
            Clock clock) {
        this.tokens = tokens;
        this.accessRevocations = accessRevocations;
        this.clock = clock;
    }

    @Override
    public void create(long userId, String familyId, String refreshHash, Instant expiresAt) {
        tokens.save(new AuthTokenEntity(userId, familyId, refreshHash,
                KstTime.toLocal(clock.instant()), KstTime.toLocal(expiresAt)));
    }

    @Override
    public Rotation rotate(String oldHash, String nextHash, Instant nextExpiresAt, Instant now) {
        LocalDateTime nowLocal = KstTime.toLocal(now);
        AuthTokenEntity current = tokens.findForRotation(oldHash).orElse(null);
        if (current == null || current.getRevokedAt() != null) {
            return new Rotation(RotationStatus.INVALID, null, null);
        }
        if (current.getUsedAt() != null) {
            // 이미 새 토큰으로 바뀐 토큰이 다시 왔다. 탈취 가능성이 있으므로 호출자가 계열 전체를 폐기한다.
            return new Rotation(RotationStatus.REUSED, current.getAccountId(), current.getFamilyId());
        }
        if (!current.getExpiresAt().isAfter(nowLocal)) {
            return new Rotation(RotationStatus.INVALID, null, null);
        }
        current.markUsed(nowLocal);
        tokens.save(new AuthTokenEntity(current.getAccountId(), current.getFamilyId(), nextHash,
                nowLocal, KstTime.toLocal(nextExpiresAt)));
        return new Rotation(RotationStatus.ROTATED, current.getAccountId(), current.getFamilyId());
    }

    @Override
    public void revokeFamily(String familyId) {
        tokens.revokeFamily(familyId, KstTime.toLocal(clock.instant()));
    }

    @Override
    public void revokeFamilyByRefreshHash(String refreshHash) {
        tokens.findByTokenHash(refreshHash)
                .ifPresent(token -> revokeFamily(token.getFamilyId()));
    }

    @Override
    public void revokeAccess(String jti, Instant expiresAt) {
        accessRevocations.revokeAccess(jti, expiresAt);
    }

    @Override
    public void revokeAllForUser(long userId, Instant at) {
        // 폐기 저장소를 먼저 쓴다. 쓸 수 없으면 예외로 중단되어 비밀번호도 바뀌지 않는다(fail-closed).
        accessRevocations.revokeUserTokensIssuedUntil(userId, at);
        tokens.revokeAllOfAccount(userId, KstTime.toLocal(at));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isAccessRevoked(String jti, long userId, Instant issuedAt) {
        return accessRevocations.isAccessRevoked(jti)
                || accessRevocations.isIssuedAtOrBeforeCutoff(userId, issuedAt);
    }
}
