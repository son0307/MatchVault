package com.son.soccerStreaming.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlayerMatchStat {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "match_record_id")
    private MatchRecord matchRecord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id")
    private Player player;

    private int goals = 0;
    private int assists = 0;
    private int shots = 0;
    private int totalPasses = 0;
    private int successfulPasses = 0;
    private int fouls = 0;
    private int tackles = 0;
}
