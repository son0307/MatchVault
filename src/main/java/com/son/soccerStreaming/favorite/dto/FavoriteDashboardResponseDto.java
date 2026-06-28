package com.son.soccerStreaming.favorite.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteDashboardResponseDto {

    private List<TeamCard> teams;
    private List<PlayerCard> players;

    public static FavoriteDashboardResponseDto empty() {
        return FavoriteDashboardResponseDto.builder()
                .teams(List.of())
                .players(List.of())
                .build();
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamCard {
        private Long teamId;
        private String teamName;
        private String teamNameKo;
        private String logoUrl;
        private Integer rank;
        private Integer points;
        private String form;
        private List<TeamFixture> recentFixtures;
        private TeamFixture nextFixture;
        private LiveTeamFixture liveFixture;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamFixture {
        private Long fixtureId;
        private LocalDateTime fixtureDate;
        private String homeTeamName;
        private String homeTeamNameKo;
        private String awayTeamName;
        private String awayTeamNameKo;
        private Integer homeScore;
        private Integer awayScore;
        private String fixtureStatus;
        private String result;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LiveTeamFixture {
        private Long fixtureId;
        private LocalDateTime fixtureDate;
        private String homeTeamName;
        private String homeTeamNameKo;
        private String awayTeamName;
        private String awayTeamNameKo;
        private Integer homeScore;
        private Integer awayScore;
        private String fixtureStatus;
        private String statusShort;
        private String statusLong;
        private Integer elapsed;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlayerCard {
        private Long playerId;
        private String playerName;
        private String playerNameKo;
        private String photoUrl;
        private String position;
        private RecentPlayerMatch recentMatch;
        private PlayerSeasonStat seasonStat;

        public PlayerCard(
                Long playerId,
                String playerName,
                String photoUrl,
                String position,
                RecentPlayerMatch recentMatch,
                PlayerSeasonStat seasonStat
        ) {
            this(playerId, playerName, null, photoUrl, position, recentMatch, seasonStat);
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentPlayerMatch {
        private Long fixtureId;
        private LocalDateTime fixtureDate;
        private String teamName;
        private String teamNameKo;
        private String opponentTeamName;
        private String opponentTeamNameKo;
        private Integer teamScore;
        private Integer opponentScore;
        private Integer minutesPlayed;
        private Double rating;
        private Integer goals;
        private Integer assists;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlayerSeasonStat {
        private Integer season;
        private String teamName;
        private String teamNameKo;
        private String teamLogoUrl;
        private Integer teamCount;
        private Boolean aggregated;
        private Integer appearances;
        private Integer minutes;
        private Double rating;
        private Integer goals;
        private Integer assists;
        private Integer yellowCards;
        private Integer redCards;
    }
}
