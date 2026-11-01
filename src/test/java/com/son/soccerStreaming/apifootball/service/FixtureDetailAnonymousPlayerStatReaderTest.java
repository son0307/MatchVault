package com.son.soccerStreaming.apifootball.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FixtureDetailAnonymousPlayerStatReaderTest {

    @Test
    void readsNamedFixtureStatWithoutTreatingZeroAsPlayerIdentity() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        var reader = new FixtureDetailAnonymousPlayerStatReader(jdbcTemplate, new ObjectMapper());
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq(7L), eq(100L)))
                .thenReturn(List.of("""
                        {"players":[{"team":{"id":42},"players":[
                          {"player":{"id":0,"name":"Younes Ibrahim"},"statistics":[{
                            "games":{"minutes":0,"rating":"0"},"shots":{"total":0,"on":0},
                            "goals":{"total":0,"assists":0},"passes":{"total":0,"accuracy":"0"}
                          }]},
                          {"player":{"id":10,"name":"Known Player"},"statistics":[{}]}
                        ]}]}
                        """));

        var stats = reader.findForFixture(7L, 100L);

        assertThat(stats).singleElement().satisfies(anonymous -> {
            assertThat(anonymous.teamId()).isEqualTo(42L);
            assertThat(anonymous.stat().getPlayerId()).isNull();
            assertThat(anonymous.stat().getPlayerName()).isEqualTo("Younes Ibrahim");
            assertThat(anonymous.stat().getMinutesPlayed()).isZero();
            assertThat(anonymous.stat().getGoals()).isZero();
            assertThat(anonymous.stat().getShotsTotal()).isZero();
        });
    }
}
