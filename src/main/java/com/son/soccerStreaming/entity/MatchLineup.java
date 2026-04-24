package com.son.soccerStreaming.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "match_lineup", indexes = {
        @Index(name = "idx_lineup_match_id", columnList = "match_record_id"),
        @Index(name = "idx_lineup_player_id", columnList = "player_id")
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchLineup {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_record_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private MatchRecord matchRecord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Player player;

    private boolean isStarting;

    private String formationPosition;
}
