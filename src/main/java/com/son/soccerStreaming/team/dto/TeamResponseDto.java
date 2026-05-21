package com.son.soccerStreaming.team.dto;

import lombok.Builder;
import lombok.Getter;

public class TeamResponseDto {

    @Getter
    @Builder
    public static class Summary {
        private Long teamId;
        private String teamName;
        private String code;
        private String logoUrl;
    }

    @Getter
    @Builder
    public static class Details {
        private Long teamId;
        private String teamName;
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
        private String venueAddress;
        private String venueCity;
        private Integer capacity;
        private String surface;
        private String venueImageUrl;
    }
}
