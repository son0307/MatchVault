package com.son.soccerStreaming.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "player_match_stat", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"match_record_id", "player_id"})
}
    , indexes = {
        @Index(name = "idx_match_record_id", columnList = "match_record_id"),
        @Index(name = "idx_team_id", columnList = "team_id"),
        @Index(name = "idx_player_id", columnList = "player_id")
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class PlayerMatchStat {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 💡 연관관계 설정
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_record_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private MatchRecord matchRecord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Player player;

    // 출전 정보 (Games)
    private Integer minutesPlayed; // 출전 시간
    private Double rating;         // 평점
    private Boolean isCaptain;     // 주장 여부
    private Boolean isSubstitute;  // 교체 출전 여부

    // 득점 및 도움 (Goals)
    private Integer goals;         // 득점
    private Integer assists;       // 도움
    private Integer conceded;      // 실점
    private Integer saves;         // 선방 (골키퍼 전용)

    // 슈팅 (Shots)
    private Integer shotsTotal;    // 총 슈팅
    private Integer shotsOnTarget; // 유효 슈팅

    // 패스 (Passes)
    private Integer passesTotal;   // 총 패스
    private Integer passesKey;     // 키 패스
    private Integer passAccuracy;  // 패스 정확도

    // 수비 (Tackles & Blocks)
    private Integer tacklesTotal;  // 총 태클
    private Integer blocks;        // 블락
    private Integer interceptions; // 가로채기

    // 경합 (Duels & Dribbles)
    private Integer duelsTotal;    // 경합 시도
    private Integer duelsWon;      // 경합 승리
    private Integer dribblesAttempts;// 드리블 시도
    private Integer dribblesSuccess; // 드리블 성공
    private Integer dribblesPast;  // 돌파 허용

    // 파울 및 카드 (Fouls & Cards)
    private Integer foulsDrawn;    // 파울 얻음
    private Integer foulsCommitted;// 파울 범함
    private Integer yellowCards;   // 경고
    private Integer redCards;      // 퇴장
    private Integer offsides;      // 오프사이드

    // 페널티 (Penalty)
    private Integer penaltyWon;    // 페널티킥 얻음
    private Integer penaltyCommitted;// 페널티킥 허용
    private Integer penaltyScored; // 페널티킥 득점
    private Integer penaltyMissed; // 페널티킥 실축
    private Integer penaltySaved;  // 페널티킥 선방 (골키퍼)
}