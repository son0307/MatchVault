package com.son.soccerStreaming.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

public class MatchLineupResponseDto {

    @Getter
    @Builder
    public static class Lineup {
        private Long matchId;
        private TeamLineup homeTeam;
        private TeamLineup awayTeam;
    }

    @Getter
    @Builder
    public static class TeamLineup {
        private Long teamId;
        private String teamName;
        private String formation;
        private String coachName;
        private List<PlayerLineup> starters;
        private List<PlayerLineup> substitutes;
        private List<PlayerAbsenceInfo> absences;
    }

    @Getter
    @Builder
    public static class PlayerLineup {
        private Long playerId;
        private String playerName;
        private Integer backNumber;
        private String position;
        private String grid;
        private boolean starter;
    }

    @Getter
    @Builder
    public static class PlayerAbsenceInfo {
        private Long playerId;
        private String playerName;
        private Long teamId;
        private String teamName;
        private String absenceType;
        private String reason;
    }
}
