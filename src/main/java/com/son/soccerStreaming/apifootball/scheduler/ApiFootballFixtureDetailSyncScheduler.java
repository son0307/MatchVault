package com.son.soccerStreaming.apifootball.scheduler;

import com.son.soccerStreaming.apifootball.service.ApiFootballFixtureDetailSyncService;
import com.son.soccerStreaming.service.LiveFixtureBroadcastService;
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
@ConditionalOnProperty(name = "api-football.sync.fixture-details.enabled", havingValue = "true")
public class ApiFootballFixtureDetailSyncScheduler {

    private final ApiFootballFixtureDetailSyncService apiFootballFixtureDetailSyncService;
    private final LiveFixtureBroadcastService liveFixtureBroadcastService;
    private final FixtureRecordRepository fixtureRecordRepository;

    @Value("${api-football.sync.fixtures.season:2025}")
    private Integer season;

    @Scheduled(cron = "${api-football.sync.fixture-details.live-cron:0 * * * * *}")
    public void syncLiveFixtureDetails() {
        try {
            apiFootballFixtureDetailSyncService.syncFixtureDetailsWithResults(
                    fixtureRecordRepository.findAllByFixtureStatus("LIVE"),
                    true
            ).forEach(result -> liveFixtureBroadcastService.broadcastFixture(result.fixtureId(), result.latestEvent()));
        } catch (Exception e) {
            log.error("API-Football live fixture detail sync failed.", e);
        }
    }

    @Scheduled(cron = "${api-football.sync.fixture-details.daily-cron:0 55 4 * * *}")
    public void syncFixtureDetailsDaily() {
        try {
            apiFootballFixtureDetailSyncService.syncSeasonFixtureDetails(season, false);
        } catch (Exception e) {
            log.error("API-Football daily fixture detail sync failed.", e);
        }
    }
}
