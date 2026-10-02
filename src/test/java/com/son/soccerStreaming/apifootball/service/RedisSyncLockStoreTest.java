package com.son.soccerStreaming.apifootball.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisSyncLockStoreTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final RedisSyncLockStore store = new RedisSyncLockStore(redis);

    @Test
    void acquisitionUsesASharedKeyAndAnExpiry() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        Duration ttl = Duration.ofMinutes(2);
        when(values.setIfAbsent("match-vault:sync:lock:fixtures:39", "owner-a", ttl)).thenReturn(true);

        assertThat(store.acquire("fixtures:39", "owner-a", ttl)).isTrue();
        verify(values).setIfAbsent("match-vault:sync:lock:fixtures:39", "owner-a", ttl);
    }

    @Test
    void renewalAndReleaseAreOwnerCheckedInRedis() {
        when(redis.execute(any(RedisScript.class), eq(List.of("match-vault:sync:lock:fixtures:39")),
                eq("owner-a"), eq("120000"))).thenReturn(1L);

        assertThat(store.renew("fixtures:39", "owner-a", Duration.ofMinutes(2))).isTrue();
        store.release("fixtures:39", "owner-a");

        verify(redis).execute(any(RedisScript.class),
                eq(List.of("match-vault:sync:lock:fixtures:39")), eq("owner-a"));
    }
}
