package com.son.soccerStreaming.apifootball.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

public final class ApiFootballLeagueDto {

    private ApiFootballLeagueDto() {
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ApiResponse<T> {
        private List<T> response;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LeagueResponse {
        private LeagueInfo league;
        private List<SeasonInfo> seasons;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LeagueInfo {
        private Integer id;
        private String name;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SeasonInfo {
        private Integer year;
        private LocalDate start;
        private LocalDate end;
        private Boolean current;
        private CoverageInfo coverage;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CoverageInfo {
        private FixtureCoverageInfo fixtures;
        private Boolean standings;
        private Boolean players;

        @JsonProperty("top_scorers")
        private Boolean topScorers;

        @JsonProperty("top_assists")
        private Boolean topAssists;

        @JsonProperty("top_cards")
        private Boolean topCards;

        private Boolean injuries;
        private Boolean predictions;
        private Boolean odds;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FixtureCoverageInfo {
        private Boolean events;
        private Boolean lineups;

        @JsonProperty("statistics_fixtures")
        private Boolean statisticsFixtures;

        @JsonProperty("statistics_players")
        private Boolean statisticsPlayers;
    }
}
