package com.son.soccerStreaming.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchRecord {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // API 통신용 고유 경기 ID
    @Column(nullable = false, unique = true)
    private Long apiFixtureId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "home_team_id", nullable = false)
    private Team homeTeam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "away_team_id", nullable = false)
    private Team awayTeam;

    // 경기 메타데이터
    @Column(nullable = false)
    private LocalDateTime matchDate;
    private String referee;
    private String round;

    // 경기장 정보
    private Long venueId;
    private String venueName;
    private String venueCity;

    // 상태 및 스코어
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private MatchStatus status;
    private Integer elapsed;

    @Builder.Default
    private Integer homeScore = 0;

    @Builder.Default
    private Integer awayScore = 0;

    // 포메이션 정보
    @Column(length = 20)
    private String homeFormation;

    @Column(length = 20)
    private String awayFormation;

    // 감독 정보
    private String homeCoachName;
    private String awayCoachName;

    public void addHomeScore() {
        homeScore++;
    }

    public void addAwayScore() {
        awayScore++;
    }

    public void updateScoreFromApi(int homeScore, int awayScore, MatchStatus status, int elapsed) {
        this.homeScore = homeScore;
        this.awayScore = awayScore;
        this.status = status;
        this.elapsed = elapsed;
    }

    public void updateTactics(String homeFormation, String awayFormation,
                              String homeCoachName, String awayCoachName) {
        this.homeFormation = homeFormation;
        this.awayFormation = awayFormation;
        this.homeCoachName = homeCoachName;
        this.awayCoachName = awayCoachName;
    }
}
