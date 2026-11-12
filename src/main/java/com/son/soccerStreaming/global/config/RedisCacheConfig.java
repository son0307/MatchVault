package com.son.soccerStreaming.global.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

import java.time.Duration;

@EnableCaching
@Configuration
public class RedisCacheConfig implements CachingConfigurer {

    public static final String TEAM_PLAYER_RANKINGS_CACHE = "teamPlayerRankings";
    public static final String LEAGUE_PLAYER_RANKINGS_CACHE = "leaguePlayerRankings";
    public static final String LEAGUE_TEAM_RANKINGS_CACHE = "leagueTeamRankings";
    public static final String FAVORITE_TEAM_CARD_CACHE = "favoriteTeamCard";
    public static final String FAVORITE_PLAYER_CARD_CACHE = "favoritePlayerCard";
    public static final String RANKINGS_CACHE_MANAGER = "rankingsCacheManager";

    @Bean
    @Override
    public CacheErrorHandler errorHandler() {
        return new RedisCacheErrorHandler();
    }

    @Bean
    @Primary
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheWriter cacheWriter = RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory);
        RedisCacheConfiguration defaultConfig = defaultCacheConfiguration();

        return RedisCacheManager.builder(cacheWriter)
                .cacheDefaults(defaultConfig)
                .build();
    }

    @Bean(name = RANKINGS_CACHE_MANAGER)
    public CacheManager rankingsCacheManager(RedisConnectionFactory connectionFactory,
                                            @Qualifier("cacheManager") RedisCacheManager defaultManager,
                                            StringRedisTemplate redis,
                                            MeterRegistry meterRegistry,
                                            Environment environment,
                                            @Value("${ranking.cache.baseline-nonlocking:false}") boolean baselineNonLocking,
                                            @Value("${ranking.cache.comparison-mode:name-locking}") String comparisonMode,
                                            @Value("${ranking.cache.lock-ttl:60s}") Duration lockTtl,
                                            @Value("${ranking.cache.max-wait:10s}") Duration maxWait,
                                            @Value("${ranking.cache.fallback-permits:2}") int fallbackPermits) {
        if (baselineNonLocking) {
            if (!environment.acceptsProfiles(Profiles.of("local"))) {
                throw new IllegalStateException("Non-locking ranking baseline is only available in the local profile");
            }
            return defaultManager;
        }
        if ("non-locking".equals(comparisonMode)) {
            return defaultManager;
        }
        if ("name-locking".equals(comparisonMode)) {
            return RedisCacheManager.builder(RedisCacheWriter.lockingRedisCacheWriter(connectionFactory))
                    .cacheDefaults(defaultCacheConfiguration())
                    .build();
        }
        if (!"per-key".equals(comparisonMode)) {
            throw new IllegalArgumentException("Unknown ranking cache comparison mode: " + comparisonMode);
        }
        RedisCacheWriter cacheWriter = RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory);
        RedisCacheConfiguration defaultConfig = defaultCacheConfiguration();

        RedisCacheManager delegate = RedisCacheManager.builder(cacheWriter)
                .cacheDefaults(defaultConfig)
                .build();
        return new RankingCacheManager(delegate, redis, meterRegistry, lockTtl, maxWait, fallbackPermits);
    }

    private RedisCacheConfiguration defaultCacheConfiguration() {
        BasicPolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .build();

        GenericJacksonJsonRedisSerializer serializer =
                GenericJacksonJsonRedisSerializer.builder()
                        .enableDefaultTyping(ptv)
                        .build();

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));
    }
}

