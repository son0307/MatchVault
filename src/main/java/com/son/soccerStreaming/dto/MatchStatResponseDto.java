package com.son.soccerStreaming.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MatchStatResponseDto {
    private String matchId;
    private TeamStatSummary homeTeam;
    private TeamStatSummary awayTeam;

    @Getter
    @Builder
    public static class TeamStatSummary {
        private String teamName;
        private int score;
        private int totalShots;
        private int shotsOnTarget;
        private int passes;
        private int passAccuracy;
        private int fouls;
        private int tackles;
    }
}
