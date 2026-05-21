package com.son.soccerStreaming.team.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "team_standing", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"team_id", "season"})
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamStanding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(nullable = false)
    private Integer season;

    @Column(name = "team_rank")
    private Integer rank;
    private Integer points;
    private Integer goalsDiff;

    @Column(name = "standing_group")
    private String group;

    private String form;

    @Column(name = "standing_status")
    private String status;

    private String description;

    private Integer played;
    private Integer win;
    private Integer draw;
    private Integer lose;
    private Integer goalsFor;
    private Integer goalsAgainst;

    private Integer homePlayed;
    private Integer homeWin;
    private Integer homeDraw;
    private Integer homeLose;
    private Integer homeGoalsFor;
    private Integer homeGoalsAgainst;

    private Integer awayPlayed;
    private Integer awayWin;
    private Integer awayDraw;
    private Integer awayLose;
    private Integer awayGoalsFor;
    private Integer awayGoalsAgainst;

    private LocalDateTime apiUpdatedAt;

    public void updateStanding(Integer rank, Integer points, Integer goalsDiff, String group,
                               String form, String status, String description,
                               Integer played, Integer win, Integer draw, Integer lose,
                               Integer goalsFor, Integer goalsAgainst,
                               Integer homePlayed, Integer homeWin, Integer homeDraw, Integer homeLose,
                               Integer homeGoalsFor, Integer homeGoalsAgainst,
                               Integer awayPlayed, Integer awayWin, Integer awayDraw, Integer awayLose,
                               Integer awayGoalsFor, Integer awayGoalsAgainst,
                               LocalDateTime apiUpdatedAt) {
        this.rank = rank;
        this.points = points;
        this.goalsDiff = goalsDiff;
        this.group = group;
        this.form = form;
        this.status = status;
        this.description = description;
        this.played = played;
        this.win = win;
        this.draw = draw;
        this.lose = lose;
        this.goalsFor = goalsFor;
        this.goalsAgainst = goalsAgainst;
        this.homePlayed = homePlayed;
        this.homeWin = homeWin;
        this.homeDraw = homeDraw;
        this.homeLose = homeLose;
        this.homeGoalsFor = homeGoalsFor;
        this.homeGoalsAgainst = homeGoalsAgainst;
        this.awayPlayed = awayPlayed;
        this.awayWin = awayWin;
        this.awayDraw = awayDraw;
        this.awayLose = awayLose;
        this.awayGoalsFor = awayGoalsFor;
        this.awayGoalsAgainst = awayGoalsAgainst;
        this.apiUpdatedAt = apiUpdatedAt;
    }
}
