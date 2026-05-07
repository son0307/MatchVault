package com.son.soccerStreaming.entity;

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
import jakarta.persistence.ForeignKey;
import jakarta.persistence.ConstraintMode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "fixture_event", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"fixture_id", "event_sequence"})
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class FixtureEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fixture_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Fixture fixture;

    @Column(nullable = false)
    private Integer eventSequence;

    private Integer elapsed;
    private Integer extra;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Player player;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assist_player_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Player assistPlayer;

    @Column(length = 50)
    private String eventType;

    @Column(length = 100)
    private String eventDetail;

    private String comments;

    public void updateEvent(Integer elapsed, Integer extra, Team team, Player player, Player assistPlayer,
                            String eventType, String eventDetail, String comments) {
        this.elapsed = elapsed;
        this.extra = extra;
        this.team = team;
        this.player = player;
        this.assistPlayer = assistPlayer;
        this.eventType = eventType;
        this.eventDetail = eventDetail;
        this.comments = comments;
    }
}
