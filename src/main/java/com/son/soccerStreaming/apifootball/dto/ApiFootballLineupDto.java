package com.son.soccerStreaming.apifootball.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

public final class ApiFootballLineupDto {

    private ApiFootballLineupDto() {
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
    public static class LineupResponse {
        private TeamInfo team;
        private CoachInfo coach;
        private String formation;
        private List<PlayerEntry> startXI;
        private List<PlayerEntry> substitutes;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TeamInfo {
        private Long id;
        private String name;
        private String logo;
        @JsonProperty("colors")
        private UniformColors uniformColors;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UniformColors {
        private ColorInfo player;
        private ColorInfo goalkeeper;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ColorInfo {
        private String primary;
        private String number;
        private String border;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CoachInfo {
        private Long id;
        private String name;
        private String photo;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlayerEntry {
        private PlayerInfo player;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlayerInfo {
        private Long id;
        private String name;
        private Integer number;
        private String pos;
        private String grid;
    }
}
