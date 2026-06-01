package com.son.soccerStreaming.league.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@Table(
        name = "league_season_coverage",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_league_season_coverage_league_season", columnNames = {"league_id", "season_year"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class LeagueSeasonCoverage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "league_id", nullable = false)
    private Integer leagueId;

    @Column(nullable = false, length = 80)
    private String leagueName;

    @Column(name = "season_year", nullable = false)
    private Integer seasonYear;

    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean currentSeason;
    private Boolean events;
    private Boolean lineups;
    private Boolean fixtureStats;
    private Boolean playerStats;
    private Boolean standings;
    private Boolean players;
    private Boolean topScorers;
    private Boolean topAssists;
    private Boolean topCards;
    private Boolean injuries;
    private Boolean predictions;
    private Boolean odds;
    private LocalDateTime syncedAt;

    public void update(
            String leagueName,
            LocalDate startDate,
            LocalDate endDate,
            Boolean currentSeason,
            Boolean events,
            Boolean lineups,
            Boolean fixtureStats,
            Boolean playerStats,
            Boolean standings,
            Boolean players,
            Boolean topScorers,
            Boolean topAssists,
            Boolean topCards,
            Boolean injuries,
            Boolean predictions,
            Boolean odds,
            LocalDateTime syncedAt
    ) {
        this.leagueName = leagueName;
        this.startDate = startDate;
        this.endDate = endDate;
        this.currentSeason = currentSeason;
        this.events = events;
        this.lineups = lineups;
        this.fixtureStats = fixtureStats;
        this.playerStats = playerStats;
        this.standings = standings;
        this.players = players;
        this.topScorers = topScorers;
        this.topAssists = topAssists;
        this.topCards = topCards;
        this.injuries = injuries;
        this.predictions = predictions;
        this.odds = odds;
        this.syncedAt = syncedAt;
    }
}
