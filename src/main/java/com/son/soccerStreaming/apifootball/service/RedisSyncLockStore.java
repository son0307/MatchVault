package com.son.soccerStreaming.apifootball.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
@ConditionalOnProperty(name = "api-football.sync.distributed-lock.enabled", havingValue = "true")
@RequiredArgsConstructor
class RedisSyncLockStore implements SyncLockStore {
    private static final String PREFIX = "match-vault:sync:lock:";
    private static final DefaultRedisScript<Long> RENEW_SCRIPT = script("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('PEXPIRE', KEYS[1], ARGV[2])
            end
            return 0
            """);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = script("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """);

    private final StringRedisTemplate redis;

    @Override
    public boolean acquire(String syncKey, String owner, Duration ttl) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key(syncKey), owner, ttl));
    }

    @Override
    public boolean renew(String syncKey, String owner, Duration ttl) {
        return Long.valueOf(1).equals(redis.execute(
                RENEW_SCRIPT, List.of(key(syncKey)), owner, Long.toString(ttl.toMillis())));
    }

    @Override
    public void release(String syncKey, String owner) {
        redis.execute(RELEASE_SCRIPT, List.of(key(syncKey)), owner);
    }

    private static String key(String syncKey) {
        return PREFIX + syncKey;
    }

    private static DefaultRedisScript<Long> script(String source) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(source);
        script.setResultType(Long.class);
        return script;
    }
}
