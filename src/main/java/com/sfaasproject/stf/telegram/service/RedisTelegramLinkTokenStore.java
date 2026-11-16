package com.sfaasproject.stf.telegram.service;

import java.time.Duration;
import java.time.Instant;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Stores only the hash of a short-lived, one-use Telegram connection token. */
@Component
public class RedisTelegramLinkTokenStore {
    private final StringRedisTemplate redis;
    private final TelegramLinkTokenService tokens;

    public RedisTelegramLinkTokenStore(StringRedisTemplate redis, TelegramLinkTokenService tokens) {
        this.redis = redis;
        this.tokens = tokens;
    }

    public void save(long userId, TelegramLinkTokenService.Issued issued, Instant now) {
        if (userId <= 0 || issued == null || now == null
                || !tokens.matches(issued.rawToken(), issued.sha256Hash())) {
            throw new IllegalArgumentException("User, token and current time are required");
        }
        Duration ttl = Duration.between(now, issued.expiresAt());
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Link token has expired");
        }
        Boolean saved = redis.opsForValue()
                .setIfAbsent(key(issued.sha256Hash()), Long.toString(userId), ttl);
        if (!Boolean.TRUE.equals(saved)) {
            throw new IllegalStateException("Link token collision");
        }
    }

    public Long consume(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return null;
        String userId = redis.opsForValue().getAndDelete(key(tokens.hashToken(rawToken)));
        return userId == null ? null : Long.parseLong(userId);
    }

    private String key(String hash) {
        return "telegram:link:" + hash;
    }
}
