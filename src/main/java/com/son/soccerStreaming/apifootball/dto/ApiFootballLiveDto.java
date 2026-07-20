package com.son.soccerStreaming.apifootball.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

public final class ApiFootballLiveDto {

    private ApiFootballLiveDto() {
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ApiResponse<T> extends ApiFootballResponseEnvelope<T> {
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FixtureResponse {
        private FixtureInfo fixture;
        private LeagueInfo league;
        private Teams teams;
        private Goals goals;
        private Score score;
        private List<EventResponse> events;
        private List<ApiFootballLineupDto.LineupResponse> lineups;
        private List<ApiFootballFixtureStatisticsDto.FixtureStatisticsResponse> statistics;
        private List<FixturePlayersResponse> players;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LeagueInfo {
        private Long id;
        private String name;
        private String country;
        private String logo;
        private String flag;
        private Integer season;
        private String round;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FixtureInfo {
        private Long id;
        private String referee;
        private String timezone;
        private String date;
        private Long timestamp;
        private Periods periods;
        private Venue venue;
        private Status status;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Periods {
        private Long first;
        private Long second;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Venue {
        private Long id;
        private String name;
        private String city;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Status {
        @JsonProperty("long")
        private String longStatus;
        @JsonProperty("short")
        private String shortStatus;
        private Integer elapsed;
        private Integer extra;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Teams {
        private TeamInfo home;
        private TeamInfo away;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TeamInfo {
        private Long id;
        private String name;
        private String logo;
        private Boolean winner;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Goals {
        private Integer home;
        private Integer away;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Score {
        private ScoreDetail halftime;
        private ScoreDetail fulltime;
        private ScoreDetail extratime;
        private ScoreDetail penalty;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ScoreDetail {
        private Integer home;
        private Integer away;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EventResponse {
        private Time time;
        private TeamInfo team;
        private PlayerInfo player;
        private PlayerInfo assist;
        private String type;
        private String detail;
        private String comments;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Time {
        private Integer elapsed;
        private Integer extra;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlayerInfo {
        private Long id;
        private String name;
        private String photo;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FixturePlayersResponse {
        private TeamInfo team;
        private List<PlayerStatResponse> players;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlayerStatResponse {
        private PlayerInfo player;
        private List<PlayerStatistics> statistics;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlayerStatistics {
        private Games games;
        private Integer offsides;
        private Shots shots;
        private GoalsStat goals;
        private Passes passes;
        private Tackles tackles;
        private Duels duels;
        private Dribbles dribbles;
        private Fouls fouls;
        private Cards cards;
        private Penalty penalty;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Games {
        private Integer minutes;
        private Integer number;
        private String position;
        private String rating;
        private Boolean captain;
        private Boolean substitute;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Shots {
        private Integer total;
        private Integer on;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GoalsStat {
        private Integer total;
        private Integer conceded;
        private Integer assists;
        private Integer saves;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Passes {
        private Integer total;
        private Integer key;
        private String accuracy;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Tackles {
        private Integer total;
        private Integer blocks;
        private Integer interceptions;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Duels {
        private Integer total;
        private Integer won;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Dribbles {
        private Integer attempts;
        private Integer success;
        private Integer past;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Fouls {
        private Integer drawn;
        private Integer committed;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Cards {
        private Integer yellow;
        private Integer red;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Penalty {
        private Integer won;
        private Integer commited;
        private Integer scored;
        private Integer missed;
        private Integer saved;
    }
}
