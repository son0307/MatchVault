package com.son.soccerStreaming.global.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

import java.time.Duration;
import java.util.Map;

@EnableCaching
@Configuration
public class RedisCacheConfig {

    public static final String TEAM_PLAYER_RANKINGS_CACHE = "teamPlayerRankings";
    public static final String LEAGUE_PLAYER_RANKINGS_CACHE = "leaguePlayerRankings";
    public static final String LEAGUE_TEAM_RANKINGS_CACHE = "leagueTeamRankings";
    public static final String FAVORITE_TEAM_CARD_CACHE = "favoriteTeamCard";
    public static final String FAVORITE_PLAYER_CARD_CACHE = "favoritePlayerCard";

    @Bean
    public RedisCacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            @Value("${app.cache.team-player-rankings-ttl:10m}") Duration teamPlayerRankingsTtl,
            @Value("${app.cache.league-player-rankings-ttl:30s}") Duration leaguePlayerRankingsTtl,
            @Value("${app.cache.league-team-rankings-ttl:30s}") Duration leagueTeamRankingsTtl,
            @Value("${app.cache.favorite-card-ttl:30s}") Duration favoriteCardTtl
    ) {

        BasicPolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .build();

        GenericJacksonJsonRedisSerializer serializer =
                GenericJacksonJsonRedisSerializer.builder()
                        .enableDefaultTyping(ptv)
                        .build();

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(Map.of(
                        TEAM_PLAYER_RANKINGS_CACHE, defaultConfig.entryTtl(teamPlayerRankingsTtl),
                        LEAGUE_PLAYER_RANKINGS_CACHE, defaultConfig.entryTtl(leaguePlayerRankingsTtl),
                        LEAGUE_TEAM_RANKINGS_CACHE, defaultConfig.entryTtl(leagueTeamRankingsTtl),
                        FAVORITE_TEAM_CARD_CACHE, defaultConfig.entryTtl(favoriteCardTtl),
                        FAVORITE_PLAYER_CARD_CACHE, defaultConfig.entryTtl(favoriteCardTtl)
                ))
                .build();
    }
}

