package com.son.soccerStreaming.apifootball.scheduler;

import com.son.soccerStreaming.apifootball.service.ApiFootballFixtureStatSyncService;
import com.son.soccerStreaming.entity.Fixture;
import com.son.soccerStreaming.repository.FixtureRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("${api-football.sync.fixture-statistics.enabled:false} && !${api-football.sync.fixture-details.enabled:false}")
public class ApiFootballFixtureStatSyncScheduler {

    private final ApiFootballFixtureStatSyncService apiFootballFixtureStatSyncService;
    private final FixtureRecordRepository fixtureRecordRepository;

    @Scheduled(cron = "${api-football.sync.fixture-statistics.live-cron:0 * * * * *}")
    public void syncLiveFixtureStats() {
        syncFixtures(fixtureRecordRepository.findAllByFixtureStatus("LIVE"), "live");
    }

    @Scheduled(cron = "${api-football.sync.fixture-statistics.daily-cron:0 50 4 * * *}")
    public void syncNonLiveFixtureStatsDaily() {
        syncFixtures(fixtureRecordRepository.findAllByFixtureStatusNot("LIVE"), "daily");
    }

    private void syncFixtures(List<Fixture> fixtures, String reason) {
        for (Fixture fixture : fixtures) {
            try {
                apiFootballFixtureStatSyncService.syncFixtureStats(fixture.getFixtureId());
            } catch (Exception e) {
                log.error("API-Football fixture stat sync failed. reason={}, fixtureId={}",
                        reason, fixture.getFixtureId(), e);
            }
        }
    }
}
