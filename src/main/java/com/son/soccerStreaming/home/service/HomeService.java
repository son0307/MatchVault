package com.son.soccerStreaming.home.service;

import com.son.soccerStreaming.favorite.dto.FavoriteDashboardResponseDto;
import com.son.soccerStreaming.favorite.service.FavoriteService;
import com.son.soccerStreaming.fixture.dto.FixtureResponseDto;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.fixture.repository.FixtureRecordRepository;
import com.son.soccerStreaming.home.dto.HomeSummaryResponseDto;
import com.son.soccerStreaming.team.dto.TeamStandingResponseDto;
import com.son.soccerStreaming.team.service.TeamStandingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HomeService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final FixtureRecordRepository fixtureRecordRepository;
    private final TeamStandingService teamStandingService;
    private final FavoriteService favoriteService;

    @Transactional(readOnly = true)
    public HomeSummaryResponseDto getSummary(Integer season, Long userId) {
        LocalDate today = LocalDate.now(KOREA_ZONE);
        LocalDateTime startDateTime = today.atStartOfDay(KOREA_ZONE)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
        LocalDateTime endDateTime = today.plusDays(1).atStartOfDay(KOREA_ZONE)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();

        List<FixtureResponseDto.Summary> todayFixtures = fixtureRecordRepository
                .findAllBySeasonAndFixtureDateGreaterThanEqualAndFixtureDateLessThanOrderByFixtureDateAsc(
                        season,
                        startDateTime,
                        endDateTime
                )
                .stream()
                .map(this::toFixtureSummary)
                .toList();

        List<TeamStandingResponseDto> standings = teamStandingService.getStandings(season);
        FavoriteDashboardResponseDto favorites = userId != null
                ? favoriteService.getDashboard(userId, season)
                : FavoriteDashboardResponseDto.empty();

        return HomeSummaryResponseDto.builder()
                .todayFixtures(todayFixtures)
                .standings(standings)
                .favorites(favorites)
                .build();
    }

    private FixtureResponseDto.Summary toFixtureSummary(Fixture fixture) {
        return FixtureResponseDto.Summary.builder()
                .fixtureId(fixture.getFixtureId())
                .fixtureDate(fixture.getFixtureDate())
                .homeTeamName(fixture.getHomeTeam().getName())
                .awayTeamName(fixture.getAwayTeam().getName())
                .homeScore(valueOf(fixture.getHomeScore()))
                .awayScore(valueOf(fixture.getAwayScore()))
                .homeWinner(fixture.getHomeWinner())
                .awayWinner(fixture.getAwayWinner())
                .halftimeHomeScore(fixture.getHalftimeHomeScore())
                .halftimeAwayScore(fixture.getHalftimeAwayScore())
                .fulltimeHomeScore(fixture.getFulltimeHomeScore())
                .fulltimeAwayScore(fixture.getFulltimeAwayScore())
                .extratimeHomeScore(fixture.getExtratimeHomeScore())
                .extratimeAwayScore(fixture.getExtratimeAwayScore())
                .penaltyHomeScore(fixture.getPenaltyHomeScore())
                .penaltyAwayScore(fixture.getPenaltyAwayScore())
                .fixtureStatus(fixture.getFixtureStatus())
                .build();
    }

    private int valueOf(Integer value) {
        return value == null ? 0 : value;
    }
}
