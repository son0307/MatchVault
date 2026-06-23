package com.son.soccerStreaming.fixture.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class FixtureMetaResponseDto {
    private LocalDate minDate;
    private LocalDate maxDate;
    private LocalDate latestStartedDate;
    private Integer minRound;
    private Integer maxRound;
}
