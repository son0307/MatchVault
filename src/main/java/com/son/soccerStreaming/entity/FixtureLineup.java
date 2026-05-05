package com.son.soccerStreaming.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "fixture_lineup", indexes = {
        @Index(name = "idx_fixture_lineup_fixture_team_starter", columnList = "fixture_id, team_id, is_starter"),
        @Index(name = "idx_fixture_lineup_player", columnList = "player_id")
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class FixtureLineup {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fixture_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Fixture fixture;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Player player;

    // 라인업 정보
    @Column(nullable = false)
    private Integer jerseyNumber;   // 등번호 (예: 17)

    @Column(nullable = false, length = 10)
    private String position;        // 포지션 (G: 골키퍼, D: 수비수, M: 미드필더, F: 공격수)

    @Column(length = 10)
    private String grid;            // 포메이션 위치 (예: "3:3" / 교체선수는 null)

    @Column(nullable = false)
    private boolean isStarter;      // true: 선발(startXI), false: 교체(substitutes)
}
