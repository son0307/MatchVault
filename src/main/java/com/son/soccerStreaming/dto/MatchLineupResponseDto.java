package com.son.soccerStreaming.dto;

import lombok.*;

import java.util.List;

public class MatchLineupResponseDto {

    @Getter
    @Builder
    public static class Lineup {
        private String matchId;
        private TeamLineup homeTeam;
        private TeamLineup awayTeam;
    }

    @Getter
    @Builder
    public static class TeamLineup {
        private String teamName;
        private List<PlayerLineup> players;
    }

    @Getter
    @Builder
    public static class PlayerLineup {
        private String playerName;
        private int backNumber;
        private String formationPosition;
        private boolean isStarting;
    }
}
