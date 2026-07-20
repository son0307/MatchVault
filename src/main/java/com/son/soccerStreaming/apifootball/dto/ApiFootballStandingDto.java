package com.son.soccerStreaming.apifootball.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

public final class ApiFootballStandingDto {

    private ApiFootballStandingDto() {
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ApiResponse<T> extends ApiFootballResponseEnvelope<T> {
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StandingResponse {
        private League league;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class League {
        private Long id;
        private String name;
        private Integer season;
        private List<List<Standing>> standings;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Standing {
        private Integer rank;
        private TeamInfo team;
        private Integer points;
        private Integer goalsDiff;
        private String group;
        private String form;
        private String status;
        private String description;
        private Record all;
        private Record home;
        private Record away;
        private String update;
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
    public static class Record {
        private Integer played;
        private Integer win;
        private Integer draw;
        private Integer lose;
        private Goals goals;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Goals {
        @JsonProperty("for")
        private Integer goalsFor;
        private Integer against;
    }
}
