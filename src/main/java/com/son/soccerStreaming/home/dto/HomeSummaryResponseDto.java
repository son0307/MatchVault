package com.son.soccerStreaming.home.dto;

import com.son.soccerStreaming.favorite.dto.FavoriteDashboardResponseDto;
import com.son.soccerStreaming.fixture.dto.FixtureResponseDto;
import com.son.soccerStreaming.team.dto.TeamStandingResponseDto;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class HomeSummaryResponseDto {

    private List<FixtureResponseDto.Summary> todayFixtures;
    private List<TeamStandingResponseDto> standings;
    private FavoriteDashboardResponseDto favorites;
}
