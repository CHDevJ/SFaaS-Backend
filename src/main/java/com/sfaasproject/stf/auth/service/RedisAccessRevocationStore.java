package com.sfaasproject.stf.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Redis 에 폐기 정보를 남은 유효시간만큼만 저장한다. Redis 오류는 그대로 던진다(fail-closed). */
@Component
public class RedisAccessRevocationStore implements AccessRevocationStore {

    static final String JTI_PREFIX = "auth:access:revoked:";
    static final String USER_CUTOFF_PREFIX = "auth:user:revoked-until:";
    /** 컷오프는 Access Token 최대 수명 뒤에는 의미가 없으므로 여유를 두고 그만큼만 보관한다. */
    private static final Duration CUTOFF_TTL = JwtAccessTokenService.LIFETIME.plusMinutes(1);

    private final StringRedisTemplate redis;
    private final Clock clock;

    public RedisAccessRevocationStore(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    @Override
    public void revokeAccess(String jti, Instant tokenExpiresAt) {
        if (jti == null || jti.isBlank() || tokenExpiresAt == null) {
            throw new IllegalArgumentException("jti and expiry are required");
        }
        Duration remaining = Duration.between(clock.instant(), tokenExpiresAt);
        if (remaining.isZero() || remaining.isNegative()) {
            return; // 이미 만료된 토큰은 어차피 거부된다.
        }
        redis.opsForValue().set(JTI_PREFIX + jti, "1", remaining);
    }

    @Override
    public boolean isAccessRevoked(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(JTI_PREFIX + jti));
    }

    @Override
    public void revokeUserTokensIssuedUntil(long userId, Instant cutoff) {
        if (cutoff == null) {
            throw new IllegalArgumentException("cutoff is required");
        }
        redis.opsForValue().set(USER_CUTOFF_PREFIX + userId,
                Long.toString(cutoff.getEpochSecond()), CUTOFF_TTL);
    }

    @Override
    public boolean isIssuedAtOrBeforeCutoff(long userId, Instant issuedAt) {
        String cutoff = redis.opsForValue().get(USER_CUTOFF_PREFIX + userId);
        if (cutoff == null) {
            return false;
        }
        // JWT 의 발급 시각은 초 단위이므로 초 단위로 비교한다.
        return issuedAt.getEpochSecond() <= Long.parseLong(cutoff);
    }
}
