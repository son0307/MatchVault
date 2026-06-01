package com.son.soccerStreaming.fixture.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

public class FixtureResponseDto {
    @Getter
    @Builder
    @AllArgsConstructor
    public static class Summary {
        private Long fixtureId;
        private LocalDateTime fixtureDate;
        private Integer season;
        private Integer round;
        private Long homeTeamId;
        private Long awayTeamId;
        private String homeTeamName;
        private String awayTeamName;
        private String homeTeamLogoUrl;
        private String awayTeamLogoUrl;
        private int homeScore;
        private int awayScore;
        private Boolean homeWinner;
        private Boolean awayWinner;
        private Integer halftimeHomeScore;
        private Integer halftimeAwayScore;
        private Integer fulltimeHomeScore;
        private Integer fulltimeAwayScore;
        private Integer extratimeHomeScore;
        private Integer extratimeAwayScore;
        private Integer penaltyHomeScore;
        private Integer penaltyAwayScore;
        private String fixtureStatus;
    }
}
