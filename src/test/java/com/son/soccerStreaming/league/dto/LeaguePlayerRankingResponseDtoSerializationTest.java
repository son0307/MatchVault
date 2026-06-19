package com.son.soccerStreaming.league.dto;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LeaguePlayerRankingResponseDtoSerializationTest {

    @Test
    void serializesAndDeserializesForRedisCache() {
        GenericJacksonJsonRedisSerializer serializer = GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(BasicPolymorphicTypeValidator.builder()
                        .allowIfBaseType(Object.class)
                        .build())
                .build();
        LeaguePlayerRankingResponseDto response = LeaguePlayerRankingResponseDto.builder()
                .leagueId(39)
                .season(2025)
                .goals(List.of(LeaguePlayerRankingResponseDto.Row.builder()
                        .rank(1)
                        .playerId(1L)
                        .playerName("Player")
                        .goals(10)
                        .build()))
                .assists(List.of())
                .attackPoints(List.of())
                .ratings(List.of())
                .minutes(List.of())
                .yellowCards(List.of())
                .redCards(List.of())
                .saves(List.of())
                .cleanSheets(List.of())
                .savePercentages(List.of())
                .build();

        byte[] bytes = serializer.serialize(response);
        Object restored = serializer.deserialize(bytes);

        assertThat(restored).isInstanceOf(LeaguePlayerRankingResponseDto.class);
    }
}
