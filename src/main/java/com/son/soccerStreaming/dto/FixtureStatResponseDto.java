package com.son.soccerStreaming.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FixtureStatResponseDto {
    private Long fixtureId;
    private TeamStatSummary homeTeamStat;
    private TeamStatSummary awayTeamStat;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamStatSummary {
        private Long teamId;
        private int score;
        private int totalShots;
        private int shotsOnTarget;
        private int totalPasses;
        private double passAccuracy;
        private int fouls;
        private int tackles;
        private int yellowCards;
        private int redCards;
    }
}
