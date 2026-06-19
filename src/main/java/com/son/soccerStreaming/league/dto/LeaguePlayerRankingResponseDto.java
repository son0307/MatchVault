package com.son.soccerStreaming.league.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaguePlayerRankingResponseDto {

    private Integer leagueId;
    private Integer season;
    private List<Row> goals;
    private List<Row> assists;
    private List<Row> attackPoints;
    private List<Row> ratings;
    private List<Row> minutes;
    private List<Row> yellowCards;
    private List<Row> redCards;
    private List<Row> saves;
    private List<Row> cleanSheets;
    private List<Row> savePercentages;

    @Getter
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Row {
        private Integer rank;
        private Long playerId;
        private String playerName;
        private String photoUrl;
        private String position;
        private Long teamId;
        private String teamName;
        private String teamLogoUrl;
        private Integer teamRank;
        private int appearances;
        private int minutes;
        private double rating;
        private int goals;
        private int penaltyGoals;
        private int assists;
        private int attackPoints;
        private int goalMatches;
        private int assistMatches;
        private int attackPointMatches;
        private int yellowCards;
        private int redCards;
        private int saves;
        private int conceded;
        private int cleanSheets;
        private Double savePercentage;
    }
}
