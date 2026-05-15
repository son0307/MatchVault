package com.son.soccerStreaming.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FixturePlayerStatResponseDto {

    private Long fixtureId;
    private TeamPlayerStats homeTeam;
    private TeamPlayerStats awayTeam;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamPlayerStats {
        private Long teamId;
        private String teamName;
        private List<PlayerStat> players;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlayerStat {
        private Long playerId;
        private String playerName;
        private Integer jerseyNumber;
        private String position;
        private Integer minutesPlayed;
        private Double rating;
        private Boolean captain;
        private Boolean substitute;
        private Integer goals;
        private Integer assists;
        private Integer conceded;
        private Integer saves;
        private Integer shotsTotal;
        private Integer shotsOnTarget;
        private Integer passesTotal;
        private Integer passesKey;
        private Integer passesAccurate;
        private Integer passAccuracy;
        private Integer tacklesTotal;
        private Integer blocks;
        private Integer interceptions;
        private Integer duelsTotal;
        private Integer duelsWon;
        private Integer dribblesAttempts;
        private Integer dribblesSuccess;
        private Integer dribblesPast;
        private Integer foulsDrawn;
        private Integer foulsCommitted;
        private Integer yellowCards;
        private Integer redCards;
        private Integer offsides;
        private Integer penaltyWon;
        private Integer penaltyCommitted;
        private Integer penaltyScored;
        private Integer penaltyMissed;
        private Integer penaltySaved;
    }
}
