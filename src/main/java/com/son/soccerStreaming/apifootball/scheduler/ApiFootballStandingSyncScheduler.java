package com.son.soccerStreaming.apifootball.scheduler;

import com.son.soccerStreaming.apifootball.service.ApiFootballStandingSyncService;
import com.son.soccerStreaming.repository.FixtureRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "api-football.sync.standings.enabled", havingValue = "true")
public class ApiFootballStandingSyncScheduler {

    private final ApiFootballStandingSyncService apiFootballStandingSyncService;
    private final FixtureRecordRepository fixtureRecordRepository;

    @Value("${api-football.sync.standings.league:39}")
    private Integer league;

    @Value("${api-football.sync.standings.season:2024}")
    private Integer season;

    @Scheduled(cron = "${api-football.sync.standings.daily-cron:0 10 4 * * *}")
    public void syncStandingsDaily() {
        syncStandings("daily");
    }

    @Scheduled(cron = "${api-football.sync.standings.live-cron:0 0 * * * *}")
    public void syncStandingsHourlyWhenLive() {
        if (!fixtureRecordRepository.existsByFixtureStatus("LIVE")) {
            return;
        }
        syncStandings("hourly-live");
    }

    private void syncStandings(String reason) {
        try {
            apiFootballStandingSyncService.syncStandings(league, season);
        } catch (Exception e) {
            log.error("API-Football standing sync failed. reason={}, league={}, season={}", reason, league, season, e);
        }
    }
}
