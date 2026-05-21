package com.son.soccerStreaming.fixture.dto;

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
        private int shotsOnGoal;
        private int shotsOffGoal;
        private int totalShots;
        private int shotsOnTarget;
        private int blockedShots;
        private int shotsInsideBox;
        private int shotsOutsideBox;
        private int totalPasses;
        private int passesAccurate;
        private double passAccuracy;
        private int fouls;
        private int cornerKicks;
        private int offsides;
        private int ballPossession;
        private int goalkeeperSaves;
        private int yellowCards;
        private int redCards;
        private Double expectedGoals;
    }
}
