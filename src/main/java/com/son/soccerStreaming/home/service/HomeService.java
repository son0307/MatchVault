package com.son.soccerStreaming.home.service;

import com.son.soccerStreaming.apifootball.service.FixtureDetailGenerationReadStore;
import com.son.soccerStreaming.apifootball.service.FixtureDetailGenerationResolver;
import com.son.soccerStreaming.favorite.dto.FavoriteDashboardResponseDto;
import com.son.soccerStreaming.favorite.service.FavoriteService;
import com.son.soccerStreaming.fixture.dto.FixtureResponseDto;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import com.son.soccerStreaming.global.util.DateTimeUtils;
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
import java.util.ArrayList;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class HomeService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final FixtureRepository fixtureRepository;
    private final TeamStandingService teamStandingService;
    private final FavoriteService favoriteService;
    private final FixtureDetailGenerationResolver generationResolver;
    private final FixtureDetailGenerationReadStore generationReadStore;

    @Transactional(readOnly = true)
    public HomeSummaryResponseDto getSummary(Integer season, Long userId) {
        LocalDate today = LocalDate.now(KOREA_ZONE);
        LocalDateTime startDateTime = today.atStartOfDay(KOREA_ZONE)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
        LocalDateTime endDateTime = today.plusDays(1).atStartOfDay(KOREA_ZONE)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();

        List<FixtureResponseDto.Summary> todayFixtures = todayFixtures(season, startDateTime, endDateTime)
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

    private List<Fixture> todayFixtures(Integer season, LocalDateTime start, LocalDateTime end) {
        Long generationId = season == null ? null : generationResolver.activeForSeason(season);
        if (generationId == null || generationId <= 0) {
            return fixtureRepository.findAllBySeasonAndFixtureDateGreaterThanEqualAndFixtureDateLessThanOrderByFixtureDateAsc(
                    season, start, end);
        }
        List<Fixture> fixtures = new ArrayList<>();
        Long cursor = null;
        while (true) {
            List<Fixture> page = generationReadStore.findRecentFixtures(
                    List.of(generationId), cursor, season, null, start, end, null, 500);
            int count = Math.min(page.size(), 500);
            fixtures.addAll(page.subList(0, count));
            if (page.size() <= 500) {
                break;
            }
            cursor = page.get(count - 1).getId();
        }
        fixtures.sort(Comparator.comparing(Fixture::getFixtureDate));
        return fixtures;
    }

    private FixtureResponseDto.Summary toFixtureSummary(Fixture fixture) {
        return FixtureResponseDto.Summary.builder()
                .fixtureId(fixture.getFixtureId())
                .fixtureDate(DateTimeUtils.utcToKorea(fixture.getFixtureDate()))
                .round(fixture.getRound())
                .homeTeamId(fixture.getHomeTeam().getTeamId())
                .awayTeamId(fixture.getAwayTeam().getTeamId())
                .homeTeamName(fixture.getHomeTeam().getName())
                .homeTeamNameKo(fixture.getHomeTeam().getKoreanName())
                .awayTeamName(fixture.getAwayTeam().getName())
                .awayTeamNameKo(fixture.getAwayTeam().getKoreanName())
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
                .statusShort(fixture.getStatusShort())
                .statusLong(fixture.getStatusLong())
                .elapsed(fixture.getElapsed())
                .extra(fixture.getExtra())
                .build();
    }

    private int valueOf(Integer value) {
        return value == null ? 0 : value;
    }
}
