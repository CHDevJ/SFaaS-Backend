package com.sfaasproject.stf.measurement.service;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** A stale transaction cannot complete or release another worker's Redis lease. */
@Component
public class RedisIngestionMessageGuard implements IngestionMessageGuard {
    private static final DefaultRedisScript<Long> COMPLETE = script(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('SET', KEYS[1], 'DONE', 'EX', 86400) and 1 "
                    + "else return 0 end");
    private static final DefaultRedisScript<Long> RELEASE = script(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('DEL', KEYS[1]) else return 0 end");

    private final StringRedisTemplate redis;
    private final ThreadLocal<Map<String, String>> claims = ThreadLocal.withInitial(HashMap::new);

    public RedisIngestionMessageGuard(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Acquisition acquire(String messageId) {
        String claim = "PROCESSING:" + UUID.randomUUID();
        String key = key(messageId);
        if (Boolean.TRUE.equals(redis.opsForValue()
                .setIfAbsent(key, claim, Duration.ofSeconds(60)))) {
            claims.get().put(messageId, claim);
            return Acquisition.ACQUIRED;
        }
        String current = redis.opsForValue().get(key);
        if ("DONE".equals(current)) return Acquisition.COMPLETED;
        if (current == null && Boolean.TRUE.equals(redis.opsForValue()
                .setIfAbsent(key, claim, Duration.ofSeconds(60)))) {
            claims.get().put(messageId, claim);
            return Acquisition.ACQUIRED;
        }
        return Acquisition.IN_PROGRESS;
    }

    @Override
    public void completed(String messageId) {
        String claim = forget(messageId);
        if (claim != null) redis.execute(COMPLETE, List.of(key(messageId)), claim);
    }

    @Override
    public void release(String messageId) {
        String claim = forget(messageId);
        if (claim != null) redis.execute(RELEASE, List.of(key(messageId)), claim);
    }

    private String forget(String messageId) {
        Map<String, String> current = claims.get();
        String claim = current.remove(messageId);
        if (current.isEmpty()) claims.remove();
        return claim;
    }

    private String key(String messageId) {
        return "ingest:message:" + messageId;
    }

    private static DefaultRedisScript<Long> script(String source) {
        var script = new DefaultRedisScript<Long>();
        script.setScriptText(source);
        script.setResultType(Long.class);
        return script;
    }
}
