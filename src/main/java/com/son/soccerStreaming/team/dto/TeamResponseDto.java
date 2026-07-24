package com.son.soccerStreaming.team.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

public class TeamResponseDto {

    @Getter
    @Builder
    public static class Summary {
        private Long teamId;
        private String teamName;
        private String teamNameKo;
        private String code;
        private String logoUrl;
    }

    @Getter
    @Builder
    public static class Details {
        private Long teamId;
        private String teamName;
        private String teamNameKo;
        private String code;
        private String country;
        private Integer founded;
        private String logoUrl;
        private VenueInfo venue;
    }

    @Getter
    @Builder
    public static class VenueInfo {
        private Long venueId;
        private String venueName;
        private String venueNameKo;
        private String venueAddress;
        private String venueCity;
        private Integer capacity;
        private String surface;
        private String venueImageUrl;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlayerRankings {
        private List<PlayerRanking> rows;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlayerRanking {
        private Long playerId;
        private String playerName;
        private String playerNameKo;
        private String photoUrl;
        private String position;
        private int goals;
        private int assists;
        private double rating;
        private int minutes;
    }
}
