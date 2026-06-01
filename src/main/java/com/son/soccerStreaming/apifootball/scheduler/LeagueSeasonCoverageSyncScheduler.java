package com.son.soccerStreaming.apifootball.scheduler;

import com.son.soccerStreaming.apifootball.service.LeagueSeasonCoverageSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "api-football.sync.league-seasons.enabled", havingValue = "true")
public class LeagueSeasonCoverageSyncScheduler {

    private final LeagueSeasonCoverageSyncService leagueSeasonCoverageSyncService;
    private final ApiFootballSyncFailureRetryScheduler failureRetryScheduler;

    @Value("${api-football.sync.league-seasons.league:39}")
    private Integer league;

    @Scheduled(cron = "${api-football.sync.league-seasons.daily-cron:0 0 3 * * *}")
    public void syncLeagueSeasons() {
        try {
            leagueSeasonCoverageSyncService.syncLeagueSeasons(league);
        } catch (Exception e) {
            log.error("API-Football league season coverage sync failed. league={}", league, e);
            failureRetryScheduler.schedule(
                    "league-seasons:%s".formatted(league),
                    "league season coverage sync league=%s".formatted(league),
                    () -> leagueSeasonCoverageSyncService.syncLeagueSeasons(league)
            );
        }
    }
}
