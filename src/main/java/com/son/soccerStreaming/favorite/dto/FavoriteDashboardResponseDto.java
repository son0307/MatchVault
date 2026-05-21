package com.son.soccerStreaming.favorite.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class FavoriteDashboardResponseDto {

    private List<TeamCard> teams;
    private List<PlayerCard> players;

    @Getter
    @Builder
    public static class TeamCard {
        private Long teamId;
        private String teamName;
        private String logoUrl;
        private Integer rank;
        private Integer points;
        private String form;
        private List<TeamFixture> recentFixtures;
    }

    @Getter
    @Builder
    public static class TeamFixture {
        private Long fixtureId;
        private LocalDateTime fixtureDate;
        private String homeTeamName;
        private String awayTeamName;
        private Integer homeScore;
        private Integer awayScore;
        private String fixtureStatus;
        private String result;
    }

    @Getter
    @Builder
    public static class PlayerCard {
        private Long playerId;
        private String playerName;
        private String photoUrl;
        private String position;
        private RecentPlayerMatch recentMatch;
        private PlayerSeasonStat seasonStat;
    }

    @Getter
    @Builder
    public static class RecentPlayerMatch {
        private Long fixtureId;
        private LocalDateTime fixtureDate;
        private String teamName;
        private String opponentTeamName;
        private Integer teamScore;
        private Integer opponentScore;
        private Integer minutesPlayed;
        private Double rating;
        private Integer goals;
        private Integer assists;
    }

    @Getter
    @Builder
    public static class PlayerSeasonStat {
        private Integer season;
        private String teamName;
        private String teamLogoUrl;
        private Integer appearances;
        private Integer minutes;
        private Double rating;
        private Integer goals;
        private Integer assists;
        private Integer yellowCards;
        private Integer redCards;
    }
}
