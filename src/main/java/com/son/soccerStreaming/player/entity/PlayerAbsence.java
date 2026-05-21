package com.son.soccerStreaming.player.entity;

import com.son.soccerStreaming.team.entity.Team;

import com.son.soccerStreaming.fixture.entity.Fixture;

import jakarta.persistence.*;
import lombok.*;

@Entity
// 💡 한 선수는 한 경기에 한 번만 결장 기록을 가지므로 유니크 제약을 겁니다.
@Table(name = "player_absence", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"player_id", "fixture_id"})
}, indexes = {
        @Index(name = "idx_player_absence_fixture_team", columnList = "fixture_id, team_id"),
        @Index(name = "idx_player_absence_team", columnList = "team_id")
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class PlayerAbsence {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 💡 누구인지
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Player player;

    // 💡 소속 팀
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Team team;

    // 💡 어떤 경기에서 빠지는지 (Fixture ID와 연결)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fixture_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Fixture fixture;

    // 💡 결장 유형 ("Missing Fixture" 확정 결장, "Questionable" 출전 불투명)
    @Column(nullable = false, length = 50)
    private String absenceType;

    // 💡 결장 사유 ("Broken ankle", "Suspended", "Illness" 등)
    @Column(nullable = false)
    private String reason;

    public void updateAbsence(String absenceType, String reason) {
        this.absenceType = absenceType;
        this.reason = reason;
    }
}
