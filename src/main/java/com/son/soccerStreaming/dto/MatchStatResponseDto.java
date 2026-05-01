package com.son.soccerStreaming.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MatchStatResponseDto {
    private String matchId;
    private TeamStatSummary homeTeamStat;
    private TeamStatSummary awayTeamStat;

    @Getter
    @Builder
    public static class TeamStatSummary {
        private String teamId;
        private int score;
        private int totalShots;
        private int shotsOnTarget;
        private int totalPasses;
        private int successfulPasses;
        private double passAccuracy;
        private int fouls;
        private int tackles;
    }
}
