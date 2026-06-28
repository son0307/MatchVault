package com.son.soccerStreaming.search.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class SearchResponseDto {

    private List<TeamResult> teams;
    private List<PlayerResult> players;
    private List<FixtureResult> fixtures;

    public static SearchResponseDto empty() {
        return SearchResponseDto.builder()
                .teams(List.of())
                .players(List.of())
                .fixtures(List.of())
                .build();
    }

    @Getter
    @Builder
    public static class TeamResult {
        private Long teamId;
        private String teamName;
        private String teamNameKo;
        private String code;
        private String logoUrl;
    }

    @Getter
    @Builder
    public static class PlayerResult {
        private Long playerId;
        private String playerName;
        private String playerNameKo;
        private String position;
        private String photoUrl;
    }

    @Getter
    @Builder
    public static class FixtureResult {
        private Long fixtureId;
        private LocalDateTime fixtureDate;
        private String homeTeamName;
        private String homeTeamNameKo;
        private String awayTeamName;
        private String awayTeamNameKo;
        private Integer homeScore;
        private Integer awayScore;
        private String fixtureStatus;
    }
}
