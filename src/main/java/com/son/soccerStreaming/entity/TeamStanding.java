package com.son.soccerStreaming.entity;

import jakarta.persistence.*;
import lombok.*;

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

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(nullable = false)
    private Integer season;

    // 순위 정보
    @Column(name = "team_rank")
    private Integer rank;
    private Integer points;
    private Integer goalsDiff;
    private String form;
    private String description;

    // 통합 성적
    private Integer played;
    private Integer win;
    private Integer draw;
    private Integer lose;
    private Integer goalsFor;
    private Integer goalsAgainst;

    // API-Sports에서 넘겨주는 update 시간
    private LocalDateTime apiUpdatedAt;

    // 순위 변동 업데이트
    // 순위 변동 업데이트용 비즈니스 메서드
    public void updateStanding(int rank, int points, int goalsDiff, String form,
                               int played, int win, int draw, int lose,
                               int goalsFor, int goalsAgainst, LocalDateTime apiUpdatedAt) {
        this.rank = rank;
        this.points = points;
        this.goalsDiff = goalsDiff;
        this.form = form;
        this.played = played;
        this.win = win;
        this.draw = draw;
        this.lose = lose;
        this.goalsFor = goalsFor;
        this.goalsAgainst = goalsAgainst;
        this.apiUpdatedAt = apiUpdatedAt;
    }

}
