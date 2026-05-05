package com.son.soccerStreaming.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class FixtureEventDto {

    private Long fixtureId;

    private TimeInfo time;
    private TeamInfo team;
    private PlayerInfo player;
    private PlayerInfo assist;

    private String type;
    private String detail;
    private String comments;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class TimeInfo {
        private Integer elapsed;
        private Integer extra;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class TeamInfo {
        private Long id;
        private String name;
        private String logo;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class PlayerInfo {
        private Long id;
        private String name;
    }
}
