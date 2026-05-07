package com.son.soccerStreaming.apifootball.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

public final class ApiFootballPlayerDto {

    private ApiFootballPlayerDto() {
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ApiResponse<T> {
        private List<T> response;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SquadResponse {
        private TeamInfo team;
        private List<SquadPlayer> players;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TeamInfo {
        private Long id;
        private String name;
        private String logo;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SquadPlayer {
        private Long id;
        private String name;
        private Integer age;
        private Integer number;
        private String position;
        private String photo;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProfileResponse {
        private ProfilePlayer player;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProfilePlayer {
        private Long id;
        private String name;
        private String firstname;
        private String lastname;
        private Integer age;
        private Birth birth;
        private String nationality;
        private String height;
        private String weight;
        private String photo;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Birth {
        private String date;
        private String place;
        private String country;
    }
}
