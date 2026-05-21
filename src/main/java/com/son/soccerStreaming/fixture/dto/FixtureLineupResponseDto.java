package com.son.soccerStreaming.fixture.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

public class FixtureLineupResponseDto {

    @Getter
    @Builder
    public static class Lineup {
        private Long fixtureId;
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
        private UniformColors colors;
        private List<PlayerLineup> starters;
        private List<PlayerLineup> substitutes;
        private List<PlayerAbsenceInfo> absences;
    }

    @Getter
    @Builder
    public static class UniformColors {
        private ColorInfo player;
        private ColorInfo goalkeeper;
    }

    @Getter
    @Builder
    public static class ColorInfo {
        private String primary;
        private String number;
        private String border;
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
