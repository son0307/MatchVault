package com.son.soccerStreaming.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiveFixtureSnapshotDto {

    private Long fixtureId;
    private String statusShort;
    private String statusLong;
    private String fixtureStatus;
    private Integer elapsed;
    private FixtureStatResponseDto.TeamStatSummary homeTeamStat;
    private FixtureStatResponseDto.TeamStatSummary awayTeamStat;
    private FixtureEventDto latestEvent;
}
