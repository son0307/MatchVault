package com.son.soccerStreaming.league.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeagueTeamRankingResponseDto {

    private Integer leagueId;
    private Integer season;
    private List<Row> goalsFor;
    private List<Row> goalsAgainst;
    private List<Row> possession;
    private List<Row> yellowCards;
    private List<Row> redCards;

    @Getter
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Row {
        private Integer rank;
        private Long teamId;
        private String teamName;
        private String teamNameKo;
        private String teamLogoUrl;
        private Integer teamRank;
        private int played;
        private int goalsFor;
        private int goalsAgainst;
        private double goalsForPerMatch;
        private double goalsAgainstPerMatch;
        private Double averagePossession;
        private int yellowCards;
        private int redCards;
    }
}
