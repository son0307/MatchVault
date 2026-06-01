package com.son.soccerStreaming.league.service;

import com.son.soccerStreaming.league.dto.LeagueSeasonCoverageResponseDto;
import com.son.soccerStreaming.league.entity.LeagueSeasonCoverage;
import com.son.soccerStreaming.league.repository.LeagueSeasonCoverageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LeagueSeasonCoverageService {

    private static final int DEFAULT_SEASON = 2025;

    private final LeagueSeasonCoverageRepository leagueSeasonCoverageRepository;

    @Transactional(readOnly = true)
    public LeagueSeasonCoverageResponseDto getSeasons(Integer leagueId) {
        List<LeagueSeasonCoverage> seasons = leagueSeasonCoverageRepository.findByLeagueIdOrderBySeasonYearDesc(leagueId);
        Integer currentSeason = leagueSeasonCoverageRepository.findFirstByLeagueIdAndCurrentSeasonTrue(leagueId)
                .map(LeagueSeasonCoverage::getSeasonYear)
                .or(() -> leagueSeasonCoverageRepository.findFirstByLeagueIdOrderBySeasonYearDesc(leagueId)
                        .map(LeagueSeasonCoverage::getSeasonYear))
                .orElse(DEFAULT_SEASON);

        return LeagueSeasonCoverageResponseDto.builder()
                .currentSeason(currentSeason)
                .seasons(seasons.stream().map(this::toDto).toList())
                .build();
    }

    private LeagueSeasonCoverageResponseDto.SeasonCoverage toDto(LeagueSeasonCoverage season) {
        return LeagueSeasonCoverageResponseDto.SeasonCoverage.builder()
                .leagueId(season.getLeagueId())
                .leagueName(season.getLeagueName())
                .seasonYear(season.getSeasonYear())
                .label(labelOf(season.getSeasonYear()))
                .startDate(season.getStartDate())
                .endDate(season.getEndDate())
                .currentSeason(season.getCurrentSeason())
                .events(season.getEvents())
                .lineups(season.getLineups())
                .fixtureStats(season.getFixtureStats())
                .playerStats(season.getPlayerStats())
                .standings(season.getStandings())
                .players(season.getPlayers())
                .topScorers(season.getTopScorers())
                .topAssists(season.getTopAssists())
                .topCards(season.getTopCards())
                .injuries(season.getInjuries())
                .predictions(season.getPredictions())
                .odds(season.getOdds())
                .build();
    }

    private String labelOf(Integer seasonYear) {
        if (seasonYear == null) {
            return "-";
        }
        return "%d/%02d".formatted(seasonYear, (seasonYear + 1) % 100);
    }
}
