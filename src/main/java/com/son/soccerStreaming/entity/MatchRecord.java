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

    @Column(nullable = false, unique = true)
    private String matchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "home_team_id")
    private Team homeTeam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "away_team_id")
    private Team awayTeam;

    @Column(nullable = false)
    private LocalDateTime matchDate;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private MatchStatus status;

    private int homeScore = 0;
    private int awayScore = 0;

    @OneToMany(mappedBy = "matchRecord", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private List<MatchLineup> lineups = new ArrayList<>();

    @OneToMany(mappedBy = "matchRecord", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private List<PlayerMatchStat> stats = new ArrayList<>();

    public void finishMatch() {
        this.status = MatchStatus.FINISHED;
    }

    public void addHomeScore() {
        this.homeScore += 1;
    }

    public void addAwayScore() {
        this.awayScore += 1;
    }
}
