package com.son.soccerStreaming.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "player_team_season_stat",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"player_id", "team_id", "league_id", "season"})
        },
        indexes = {
                @Index(name = "idx_player_team_season_stat_player", columnList = "player_id"),
                @Index(name = "idx_player_team_season_stat_team_season", columnList = "team_id, season")
        })
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class PlayerTeamSeasonStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Player player;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Team team;

    @Column(name = "league_id", nullable = false)
    private Long leagueId;

    @Column(nullable = false)
    private Integer season;

    private Integer backNumber;
    private String position;
    private Integer appearances;
    private Integer lineups;
    private Integer minutes;
    private Double rating;
    private Boolean captain;
    private Integer substitutesIn;
    private Integer substitutesOut;
    private Integer substitutesBench;
    private Integer shotsTotal;
    private Integer shotsOnTarget;
    private Integer goals;
    private Integer conceded;
    private Integer assists;
    private Integer saves;
    private Integer passesTotal;
    private Integer passesKey;
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
    private Integer yellowRedCards;
    private Integer redCards;
    private Integer penaltyWon;
    private Integer penaltyCommitted;
    private Integer penaltyScored;
    private Integer penaltyMissed;
    private Integer penaltySaved;

    // /players?team=&season= 응답의 statistics 한 항목을 시즌-팀 누적 기록으로 반영한다.
    public void updateSeasonStat(
            Integer backNumber,
            String position,
            Integer appearances,
            Integer lineups,
            Integer minutes,
            Double rating,
            Boolean captain,
            Integer substitutesIn,
            Integer substitutesOut,
            Integer substitutesBench,
            Integer shotsTotal,
            Integer shotsOnTarget,
            Integer goals,
            Integer conceded,
            Integer assists,
            Integer saves,
            Integer passesTotal,
            Integer passesKey,
            Integer passAccuracy,
            Integer tacklesTotal,
            Integer blocks,
            Integer interceptions,
            Integer duelsTotal,
            Integer duelsWon,
            Integer dribblesAttempts,
            Integer dribblesSuccess,
            Integer dribblesPast,
            Integer foulsDrawn,
            Integer foulsCommitted,
            Integer yellowCards,
            Integer yellowRedCards,
            Integer redCards,
            Integer penaltyWon,
            Integer penaltyCommitted,
            Integer penaltyScored,
            Integer penaltyMissed,
            Integer penaltySaved
    ) {
        this.backNumber = backNumber;
        this.position = position;
        this.appearances = appearances;
        this.lineups = lineups;
        this.minutes = minutes;
        this.rating = rating;
        this.captain = captain;
        this.substitutesIn = substitutesIn;
        this.substitutesOut = substitutesOut;
        this.substitutesBench = substitutesBench;
        this.shotsTotal = shotsTotal;
        this.shotsOnTarget = shotsOnTarget;
        this.goals = goals;
        this.conceded = conceded;
        this.assists = assists;
        this.saves = saves;
        this.passesTotal = passesTotal;
        this.passesKey = passesKey;
        this.passAccuracy = passAccuracy;
        this.tacklesTotal = tacklesTotal;
        this.blocks = blocks;
        this.interceptions = interceptions;
        this.duelsTotal = duelsTotal;
        this.duelsWon = duelsWon;
        this.dribblesAttempts = dribblesAttempts;
        this.dribblesSuccess = dribblesSuccess;
        this.dribblesPast = dribblesPast;
        this.foulsDrawn = foulsDrawn;
        this.foulsCommitted = foulsCommitted;
        this.yellowCards = yellowCards;
        this.yellowRedCards = yellowRedCards;
        this.redCards = redCards;
        this.penaltyWon = penaltyWon;
        this.penaltyCommitted = penaltyCommitted;
        this.penaltyScored = penaltyScored;
        this.penaltyMissed = penaltyMissed;
        this.penaltySaved = penaltySaved;
    }

    // Fill missing season-specific squad number from lineup data.
    public void updateBackNumberFromLineup(Integer backNumber) {
        if (backNumber != null) {
            this.backNumber = backNumber;
        }
    }
}
