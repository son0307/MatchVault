package com.son.soccerStreaming.league.dto;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LeagueTeamRankingResponseDtoSerializationTest {

    @Test
    void serializesAndDeserializesForRedisCache() {
        GenericJacksonJsonRedisSerializer serializer = GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(BasicPolymorphicTypeValidator.builder()
                        .allowIfBaseType(Object.class)
                        .build())
                .build();
        LeagueTeamRankingResponseDto response = LeagueTeamRankingResponseDto.builder()
                .leagueId(39)
                .season(2025)
                .goalsFor(List.of(LeagueTeamRankingResponseDto.Row.builder()
                        .rank(1)
                        .teamId(42L)
                        .teamName("Arsenal")
                        .goalsFor(70)
                        .goalsForPerMatch(1.84)
                        .build()))
                .goalsAgainst(List.of())
                .possession(List.of())
                .yellowCards(List.of())
                .redCards(List.of())
                .build();

        byte[] bytes = serializer.serialize(response);
        Object restored = serializer.deserialize(bytes);

        assertThat(restored).isInstanceOf(LeagueTeamRankingResponseDto.class);
    }
}
