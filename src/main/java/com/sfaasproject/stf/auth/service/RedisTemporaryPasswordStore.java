package com.sfaasproject.stf.auth.service;

import java.time.Duration;
import java.time.Instant;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 만료 시각을 Redis 에 저장한다. 키는 만료 뒤에도 한동안(30일) 남겨 둬서, 만료 후 처음 로그인을 시도할 때
 * 만료를 감지하고 DB 의 임시 비밀번호를 지울 수 있게 한다.
 */
@Component
public class RedisTemporaryPasswordStore implements TemporaryPasswordStore {

    static final String PREFIX = "auth:temp-password:expires:";
    private static final Duration RETENTION = Duration.ofDays(30);

    private final StringRedisTemplate redis;

    public RedisTemporaryPasswordStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void issued(long userId, Instant expiresAt) {
        redis.opsForValue().set(PREFIX + userId, Long.toString(expiresAt.getEpochSecond()), RETENTION);
    }

    @Override
    public boolean isExpired(long userId, Instant now) {
        String value = redis.opsForValue().get(PREFIX + userId);
        return value != null && now.getEpochSecond() >= Long.parseLong(value);
    }

    @Override
    public void clear(long userId) {
        redis.delete(PREFIX + userId);
    }
}
