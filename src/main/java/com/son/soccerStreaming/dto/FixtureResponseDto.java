package com.son.soccerStreaming.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public class FixtureResponseDto {
    @Getter
    @Builder
    @AllArgsConstructor
    public static class Summary {
        private Long fixtureId;
        private String homeTeamName;
        private String awayTeamName;
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
