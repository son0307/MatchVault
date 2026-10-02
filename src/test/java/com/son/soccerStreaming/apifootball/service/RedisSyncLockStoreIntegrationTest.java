package com.son.soccerStreaming.apifootball.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "REDIS_STREAM_INTEGRATION", matches = "true")
class RedisSyncLockStoreIntegrationTest {

    @Test
    void ownershipChecksWorkAcrossTwoStoreInstances() throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("REDIS_STREAM_INTEGRATION_PORT", "6379"));
        LettuceConnectionFactory factory = new LettuceConnectionFactory("127.0.0.1", port);
        factory.afterPropertiesSet();
        StringRedisTemplate redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
        String syncKey = "integration:" + UUID.randomUUID();
        RedisSyncLockStore blue = new RedisSyncLockStore(redis);
        RedisSyncLockStore green = new RedisSyncLockStore(redis);
        Duration ttl = Duration.ofMinutes(2);

        try {
            assertThat(blue.acquire(syncKey, "owner-blue", ttl)).isTrue();
            assertThat(green.acquire(syncKey, "owner-green", ttl)).isFalse();
            assertThat(blue.renew(syncKey, "owner-blue", ttl)).isTrue();
            assertThat(green.renew(syncKey, "owner-green", ttl)).isFalse();

            green.release(syncKey, "owner-green");
            assertThat(green.acquire(syncKey, "owner-green", ttl)).isFalse();
            blue.release(syncKey, "owner-blue");
            assertThat(green.acquire(syncKey, "owner-green", ttl)).isTrue();
        } finally {
            redis.delete("match-vault:sync:lock:" + syncKey);
            factory.destroy();
        }
    }
}
