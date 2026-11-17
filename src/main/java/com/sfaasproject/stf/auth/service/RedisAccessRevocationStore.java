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
    static final String USER_CUTOFF_PREFIX = "auth:user:revoked-at:v7:";
    static final String LEGACY_USER_CUTOFF_PREFIX = "auth:user:revoked-until:";
    private static final java.time.format.DateTimeFormatter PRECISE = new java.time.format.DateTimeFormatterBuilder().appendInstant(9).toFormatter();
    private static final org.springframework.data.redis.core.script.DefaultRedisScript<Long> MAX_CUTOFF =
            new org.springframework.data.redis.core.script.DefaultRedisScript<>(
                    "local old=redis.call('GET',KEYS[1]); if not old or old<ARGV[1] then redis.call('SET',KEYS[1],ARGV[1],'EX',ARGV[2]); end; return 1", Long.class);
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
        redis.execute(MAX_CUTOFF, java.util.List.of(USER_CUTOFF_PREFIX + userId), PRECISE.format(cutoff), Long.toString(CUTOFF_TTL.toSeconds()));
    }

    @Override
    public boolean isIssuedAtOrBeforeCutoff(long userId, Instant issuedAt) {
        String cutoff = redis.opsForValue().get(USER_CUTOFF_PREFIX + userId);
        String legacy = redis.opsForValue().get(LEGACY_USER_CUTOFF_PREFIX + userId);
        return (cutoff != null && !issuedAt.isAfter(Instant.parse(cutoff)))
                || (legacy != null && issuedAt.getEpochSecond() <= Long.parseLong(legacy));
    }
}
