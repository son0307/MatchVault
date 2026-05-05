package com.son.soccerStreaming.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@NoArgsConstructor // 💡 Jackson이 JSON을 객체로 만들 때 사용하는 기본 생성자
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class FixtureEventDto {

    // 파이썬 시뮬레이터가 이미 주입해서 보내준 경기 ID를 고스란히 받습니다.
    private Long fixtureId;

    private TimeInfo time;
    private TeamInfo team;
    private PlayerInfo player;
    private PlayerInfo assist;

    private String type;
    private String detail;
    private String comments;

    @Getter
    @NoArgsConstructor
    @ToString
    public static class TimeInfo {
        private Integer elapsed;
        private Integer extra;
    }

    @Getter
    @NoArgsConstructor
    @ToString
    public static class TeamInfo {
        private Long id;
        private String name;
        private String logo;
    }

    @Getter
    @NoArgsConstructor
    @ToString
    public static class PlayerInfo {
        private Long id;
        private String name;
    }
}