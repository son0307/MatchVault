package com.son.soccerStreaming.fixture.entity;

import com.son.soccerStreaming.team.entity.Team;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class Fixture {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // API 통신용 고유 경기 ID
    @Column(nullable = false, unique = true)
    private Long fixtureId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "home_team_id", nullable = false)
    private Team homeTeam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "away_team_id", nullable = false)
    private Team awayTeam;

    // 경기 메타데이터
    @Column(nullable = false)
    private LocalDateTime fixtureDate;
    private String referee;
    private String timezone;
    private Long timestamp;
    private Long firstPeriod;
    private Long secondPeriod;
    private String round;
    private Integer season;

    // 경기장 정보
    private Long venueId;
    private String venueName;
    private String venueCity;

    // 상태 및 스코어
    @Column(length = 10)
    private String statusShort;
    private String statusLong;
    private Integer elapsed;

    // 조회를 간단히 하기 위한 매크로 상태
    @Column(length = 20)
    private String fixtureStatus;

    private Integer homeScore;
    private Integer awayScore;
    private Boolean homeWinner;
    private Boolean awayWinner;

    private Integer halftimeHomeScore;
    private Integer halftimeAwayScore;
    private Integer fulltimeHomeScore;
    private Integer fulltimeAwayScore;
    private Integer extratimeHomeScore;
    private Integer extratimeAwayScore;
    private Integer penaltyHomeScore;
    private Integer penaltyAwayScore;

    // 포메이션 정보
    @Column(length = 20)
    private String homeFormation;

    @Column(length = 20)
    private String awayFormation;

    // 감독 정보
    private String homeCoachName;
    private String awayCoachName;

    @Column(length = 20)
    private String homePlayerColorPrimary;
    @Column(length = 20)
    private String homePlayerColorNumber;
    @Column(length = 20)
    private String homePlayerColorBorder;
    @Column(length = 20)
    private String homeGoalkeeperColorPrimary;
    @Column(length = 20)
    private String homeGoalkeeperColorNumber;
    @Column(length = 20)
    private String homeGoalkeeperColorBorder;
    @Column(length = 20)
    private String awayPlayerColorPrimary;
    @Column(length = 20)
    private String awayPlayerColorNumber;
    @Column(length = 20)
    private String awayPlayerColorBorder;
    @Column(length = 20)
    private String awayGoalkeeperColorPrimary;
    @Column(length = 20)
    private String awayGoalkeeperColorNumber;
    @Column(length = 20)
    private String awayGoalkeeperColorBorder;

    public void updateFixtureState(String statusShort, String statusLong, String fixtureStatus,
                                 Integer elapsed, Integer homeScore, Integer awayScore) {
        this.statusShort = statusShort;
        this.statusLong = statusLong;
        this.fixtureStatus = fixtureStatus;
        this.elapsed = elapsed;
        this.homeScore = homeScore;
        this.awayScore = awayScore;
    }

    public void updateFixtureMetadata(LocalDateTime fixtureDate, String referee, String timezone,
                                      Long timestamp, Long firstPeriod, Long secondPeriod,
                                      Long venueId, String venueName, String venueCity) {
        this.fixtureDate = fixtureDate;
        this.referee = referee;
        this.timezone = timezone;
        this.timestamp = timestamp;
        this.firstPeriod = firstPeriod;
        this.secondPeriod = secondPeriod;
        this.venueId = venueId;
        this.venueName = venueName;
        this.venueCity = venueCity;
    }

    public void updateTeamResult(Boolean homeWinner, Boolean awayWinner) {
        this.homeWinner = homeWinner;
        this.awayWinner = awayWinner;
    }

    public void updateScoreBreakdown(Integer halftimeHomeScore, Integer halftimeAwayScore,
                                     Integer fulltimeHomeScore, Integer fulltimeAwayScore,
                                     Integer extratimeHomeScore, Integer extratimeAwayScore,
                                     Integer penaltyHomeScore, Integer penaltyAwayScore) {
        this.halftimeHomeScore = halftimeHomeScore;
        this.halftimeAwayScore = halftimeAwayScore;
        this.fulltimeHomeScore = fulltimeHomeScore;
        this.fulltimeAwayScore = fulltimeAwayScore;
        this.extratimeHomeScore = extratimeHomeScore;
        this.extratimeAwayScore = extratimeAwayScore;
        this.penaltyHomeScore = penaltyHomeScore;
        this.penaltyAwayScore = penaltyAwayScore;
    }

    public void updateTactics(String homeFormation, String awayFormation,
                              String homeCoachName, String awayCoachName) {
        this.homeFormation = homeFormation;
        this.awayFormation = awayFormation;
        this.homeCoachName = homeCoachName;
        this.awayCoachName = awayCoachName;
    }

    public void updateRound(String round) {
        this.round = round;
    }

    public void updateSeason(Integer season) {
        this.season = season;
    }

    public void updateHomeLineupColors(String playerPrimary, String playerNumber, String playerBorder,
                                       String goalkeeperPrimary, String goalkeeperNumber, String goalkeeperBorder) {
        this.homePlayerColorPrimary = playerPrimary;
        this.homePlayerColorNumber = playerNumber;
        this.homePlayerColorBorder = playerBorder;
        this.homeGoalkeeperColorPrimary = goalkeeperPrimary;
        this.homeGoalkeeperColorNumber = goalkeeperNumber;
        this.homeGoalkeeperColorBorder = goalkeeperBorder;
    }

    public void updateAwayLineupColors(String playerPrimary, String playerNumber, String playerBorder,
                                       String goalkeeperPrimary, String goalkeeperNumber, String goalkeeperBorder) {
        this.awayPlayerColorPrimary = playerPrimary;
        this.awayPlayerColorNumber = playerNumber;
        this.awayPlayerColorBorder = playerBorder;
        this.awayGoalkeeperColorPrimary = goalkeeperPrimary;
        this.awayGoalkeeperColorNumber = goalkeeperNumber;
        this.awayGoalkeeperColorBorder = goalkeeperBorder;
    }
}
