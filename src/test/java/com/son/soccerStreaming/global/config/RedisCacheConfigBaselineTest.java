package com.son.soccerStreaming.global.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RedisCacheConfigBaselineTest {

    private final RedisCacheConfig config = new RedisCacheConfig();
    private final RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
    private final RedisCacheManager defaultManager = mock(RedisCacheManager.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();

    @Test
    void baselineUsesTheActualDefaultNonLockingManager() {
        CacheManager selected = config.rankingsCacheManager(connectionFactory, defaultManager, redis,
                metrics, true, Duration.ofSeconds(60), Duration.ofSeconds(10), 2);

        assertThat(selected).isSameAs(defaultManager);
    }

    @Test
    void normalModeKeepsPerKeyLocking() {
        CacheManager selected = config.rankingsCacheManager(connectionFactory, defaultManager, redis,
                metrics, false, Duration.ofSeconds(60), Duration.ofSeconds(10), 2);

        assertThat(selected).isInstanceOf(RankingCacheManager.class);
    }

}
