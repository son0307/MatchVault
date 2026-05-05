package com.son.soccerStreaming.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class TeamStandingResponseDto {

    private Integer season;
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
    private LocalDateTime updatedAt;

    @Getter
    @Builder
    public static class TeamInfo {
        private Long id;
        private String name;
        private String logo;
    }

    @Getter
    @Builder
    public static class Record {
        private Integer played;
        private Integer win;
        private Integer draw;
        private Integer lose;
        private Goals goals;
    }

    @Getter
    @Builder
    public static class Goals {
        private Integer goalsFor;
        private Integer goalsAgainst;
    }
}
