package com.son.soccerStreaming.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class FixtureEventResponseDto {

    private Long fixtureId;
    private List<Event> events;

    @Getter
    @Builder
    public static class Event {
        private Integer sequence;
        private TimeInfo time;
        private TeamInfo team;
        private PlayerInfo player;
        private PlayerInfo assist;
        private String type;
        private String detail;
        private String comments;
    }

    @Getter
    @Builder
    public static class TimeInfo {
        private Integer elapsed;
        private Integer extra;
    }

    @Getter
    @Builder
    public static class TeamInfo {
        private Long id;
        private String name;
        private String logo;
    }

    @Getter
    @Builder
    public static class PlayerInfo {
        private Long id;
        private String name;
    }
}
