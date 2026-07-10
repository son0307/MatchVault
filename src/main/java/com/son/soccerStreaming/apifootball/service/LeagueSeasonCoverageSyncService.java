package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.apifootball.client.ApiFootballClient;
import com.son.soccerStreaming.apifootball.dto.ApiFootballLeagueDto;
import com.son.soccerStreaming.league.entity.LeagueSeasonCoverage;
import com.son.soccerStreaming.league.repository.LeagueSeasonCoverageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeagueSeasonCoverageSyncService {

    private final ApiFootballClient apiFootballClient;
    private final LeagueSeasonCoverageRepository leagueSeasonCoverageRepository;
    private final ApiFootballSyncStatusService apiFootballSyncStatusService;

    @Transactional
    public int syncLeagueSeasons(Integer leagueId) {
        apiFootballSyncStatusService.recordAttempt("league-seasons:%s".formatted(leagueId), "League Seasons");
        List<ApiFootballLeagueDto.LeagueResponse> responses = apiFootballClient.getLeagueSeasons(leagueId);
        LocalDateTime syncedAt = LocalDateTime.now();
        int syncedCount = 0;

        for (ApiFootballLeagueDto.LeagueResponse response : responses) {
            if (response.getLeague() == null || response.getLeague().getId() == null || response.getSeasons() == null) {
                continue;
            }

            Integer responseLeagueId = response.getLeague().getId();
            String leagueName = response.getLeague().getName();
            for (ApiFootballLeagueDto.SeasonInfo season : response.getSeasons()) {
                if (season.getYear() == null) {
                    continue;
                }

                LeagueSeasonCoverage coverage = leagueSeasonCoverageRepository
                        .findByLeagueIdAndSeasonYear(responseLeagueId, season.getYear())
                        .orElseGet(() -> LeagueSeasonCoverage.builder()
                                .leagueId(responseLeagueId)
                                .leagueName(leagueName)
                                .seasonYear(season.getYear())
                                .build());

                ApiFootballLeagueDto.CoverageInfo coverageInfo = season.getCoverage();
                ApiFootballLeagueDto.FixtureCoverageInfo fixtureCoverage = coverageInfo == null ? null : coverageInfo.getFixtures();
                coverage.update(
                        leagueName,
                        season.getStart(),
                        season.getEnd(),
                        valueOf(season.getCurrent()),
                        fixtureCoverage != null && valueOf(fixtureCoverage.getEvents()),
                        fixtureCoverage != null && valueOf(fixtureCoverage.getLineups()),
                        fixtureCoverage != null && valueOf(fixtureCoverage.getStatisticsFixtures()),
                        fixtureCoverage != null && valueOf(fixtureCoverage.getStatisticsPlayers()),
                        coverageInfo != null && valueOf(coverageInfo.getStandings()),
                        coverageInfo != null && valueOf(coverageInfo.getPlayers()),
                        coverageInfo != null && valueOf(coverageInfo.getTopScorers()),
                        coverageInfo != null && valueOf(coverageInfo.getTopAssists()),
                        coverageInfo != null && valueOf(coverageInfo.getTopCards()),
                        coverageInfo != null && valueOf(coverageInfo.getInjuries()),
                        coverageInfo != null && valueOf(coverageInfo.getPredictions()),
                        coverageInfo != null && valueOf(coverageInfo.getOdds()),
                        syncedAt
                );
                leagueSeasonCoverageRepository.save(coverage);
                syncedCount++;
            }
        }

        log.info("API-Football league season coverage sync completed. league={}, count={}", leagueId, syncedCount);
        apiFootballSyncStatusService.recordSuccess("league-seasons:%s".formatted(leagueId), "League Seasons");
        return syncedCount;
    }

    private boolean valueOf(Boolean value) {
        return Boolean.TRUE.equals(value);
    }
}
