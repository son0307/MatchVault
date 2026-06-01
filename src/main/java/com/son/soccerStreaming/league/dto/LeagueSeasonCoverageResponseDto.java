package com.son.soccerStreaming.league.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class LeagueSeasonCoverageResponseDto {

    private Integer currentSeason;
    private List<SeasonCoverage> seasons;

    @Getter
    @Builder
    public static class SeasonCoverage {
        private Integer leagueId;
        private String leagueName;
        private Integer seasonYear;
        private String label;
        private LocalDate startDate;
        private LocalDate endDate;
        private Boolean currentSeason;
        private Boolean events;
        private Boolean lineups;
        private Boolean fixtureStats;
        private Boolean playerStats;
        private Boolean standings;
        private Boolean players;
        private Boolean topScorers;
        private Boolean topAssists;
        private Boolean topCards;
        private Boolean injuries;
        private Boolean predictions;
        private Boolean odds;
    }
}
