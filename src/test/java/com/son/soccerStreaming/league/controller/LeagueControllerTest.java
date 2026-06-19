package com.son.soccerStreaming.league.controller;

import com.son.soccerStreaming.league.dto.LeaguePlayerRankingResponseDto;
import com.son.soccerStreaming.league.service.LeaguePlayerRankingService;
import com.son.soccerStreaming.league.service.LeagueSeasonCoverageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LeagueControllerTest {

    private LeaguePlayerRankingService rankingService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LeagueSeasonCoverageService seasonCoverageService = mock(LeagueSeasonCoverageService.class);
        rankingService = mock(LeaguePlayerRankingService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new LeagueController(seasonCoverageService, rankingService))
                .build();
    }

    @Test
    void getsPremierLeaguePlayerRankings() throws Exception {
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
        when(rankingService.getRankings(39, 2025)).thenReturn(response);

        mockMvc.perform(get("/api/v1/leagues/39/player-rankings").param("season", "2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leagueId").value(39))
                .andExpect(jsonPath("$.goals[0].playerName").value("Player"))
                .andExpect(jsonPath("$.goals[0].goals").value(10));

        verify(rankingService).getRankings(39, 2025);
    }

    @Test
    void rejectsUnsupportedLeague() throws Exception {
        mockMvc.perform(get("/api/v1/leagues/140/player-rankings").param("season", "2025"))
                .andExpect(status().isBadRequest());
    }
}
