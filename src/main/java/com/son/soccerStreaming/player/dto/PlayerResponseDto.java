package com.son.soccerStreaming.player.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class PlayerResponseDto {

    @Getter
    @Builder
    public static class Summary {
        private Long playerId;
        private String playerName;
        private Integer backNumber;
        private String position;
        private String photoUrl;
    }

    @Getter
    @Builder
    public static class Details {
        private Long playerId;
        private String playerName;
        private String firstname;
        private String lastname;
        private Integer backNumber;
        private Integer age;
        private LocalDate birthDate;
        private String birthPlace;
        private String birthCountry;
        private String nationality;
        private String height;
        private String weight;
        private String position;
        private String photoUrl;
        private Long teamId;
        private String teamName;
        private String teamLogoUrl;
    }

    @Getter
    @Builder
    public static class Panel {
        private Details profile;
        private List<SeasonSummary> seasons;
        private List<MatchStat> matches;
    }

    @Getter
    @Builder
    public static class SeasonSummary {
        private Integer season;
        private long totalFixtures;
        private int minutesPlayed;
        private double averageRating;
        private int goals;
        private int assists;
        private int shots;
        private int shotsOnTarget;
        private int keyPasses;
        private int yellowCards;
        private int redCards;
        private List<TeamSeasonSummary> teams;
    }

    @Getter
    @Builder
    public static class TeamSeasonSummary {
        private Long teamId;
        private String teamName;
        private String teamLogoUrl;
        private long totalFixtures;
        private int minutesPlayed;
        private double averageRating;
        private int goals;
        private int assists;
        private int shots;
        private int shotsOnTarget;
        private int keyPasses;
        private int yellowCards;
        private int redCards;
    }

    @Getter
    @Builder
    public static class MatchStat {
        private Long fixtureId;
        private LocalDateTime fixtureDate;
        private Integer season;
        private String round;
        private Long teamId;
        private String teamName;
        private Long opponentTeamId;
        private String opponentTeamName;
        private Integer teamScore;
        private Integer opponentScore;
        private Integer minutesPlayed;
        private Double rating;
        private Integer goals;
        private Integer assists;
    }
}
