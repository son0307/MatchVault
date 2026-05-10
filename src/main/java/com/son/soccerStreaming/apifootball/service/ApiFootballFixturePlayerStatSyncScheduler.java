package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.entity.Fixture;
import com.son.soccerStreaming.repository.FixtureRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "api-football.sync.fixture-player-stats.enabled", havingValue = "true")
public class ApiFootballFixturePlayerStatSyncScheduler {

    private final ApiFootballFixturePlayerStatSyncService apiFootballFixturePlayerStatSyncService;
    private final FixtureRecordRepository fixtureRecordRepository;

    @Scheduled(cron = "${api-football.sync.fixture-player-stats.live-cron:0 * * * * *}")
    public void syncLiveFixturePlayerStats() {
        syncFixtures(fixtureRecordRepository.findAllByFixtureStatus("LIVE"), "live");
    }

    @Scheduled(cron = "${api-football.sync.fixture-player-stats.daily-cron:0 45 4 * * *}")
    public void syncNonLiveFixturePlayerStatsDaily() {
        syncFixtures(fixtureRecordRepository.findAllByFixtureStatusNot("LIVE"), "daily");
    }

    private void syncFixtures(List<Fixture> fixtures, String reason) {
        for (Fixture fixture : fixtures) {
            try {
                apiFootballFixturePlayerStatSyncService.syncPlayerStats(fixture.getFixtureId());
            } catch (Exception e) {
                log.error("API-Football fixture player stat sync failed. reason={}, fixtureId={}",
                        reason, fixture.getFixtureId(), e);
            }
        }
    }
}
