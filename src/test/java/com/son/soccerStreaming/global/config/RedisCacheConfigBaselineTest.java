package com.son.soccerStreaming.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RedisCacheConfigBaselineTest {

    private final RedisCacheConfig config = new RedisCacheConfig();
    private final RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
    private final RedisCacheManager defaultManager = mock(RedisCacheManager.class);

    @Test
    void baselineUsesTheActualDefaultNonLockingManager() {
        CacheManager selected = config.rankingsCacheManager(connectionFactory, defaultManager, true);

        assertThat(selected).isSameAs(defaultManager);
    }

    @Test
    void normalModeUsesSeparateLockingRedisCacheManager() {
        CacheManager selected = config.rankingsCacheManager(connectionFactory, defaultManager, false);

        assertThat(selected).isInstanceOf(RedisCacheManager.class).isNotSameAs(defaultManager);
    }

}
