package com.son.soccerStreaming.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "player_match_stat", indexes = {
        @Index(name = "idx_stat_match_id", columnList = "match_record_id"),
        @Index(name = "idx_stat_player_id", columnList = "player_id")
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class PlayerMatchStat {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "match_record_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private MatchRecord matchRecord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Player player;

    private int goals = 0;
    private int assists = 0;
    private int shots = 0;
    private int shotsOnTarget = 0;
    private int totalPasses = 0;
    private int successfulPasses = 0;
    private int fouls = 0;
    private int tackles = 0;
}
