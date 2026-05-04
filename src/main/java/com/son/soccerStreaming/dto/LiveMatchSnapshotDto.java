package com.son.soccerStreaming.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiveMatchSnapshotDto {

    private Long fixtureId;
    private String statusShort;
    private String statusLong;
    private String matchCategory;
    private Integer elapsed;
    private MatchStatResponseDto.TeamStatSummary homeTeamStat;
    private MatchStatResponseDto.TeamStatSummary awayTeamStat;
    private MatchEventDto latestEvent;
}
