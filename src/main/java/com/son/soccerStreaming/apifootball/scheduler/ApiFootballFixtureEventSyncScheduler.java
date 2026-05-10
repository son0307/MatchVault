package com.son.soccerStreaming.apifootball.scheduler;

import com.son.soccerStreaming.entity.Fixture;
import com.son.soccerStreaming.apifootball.service.ApiFootballFixtureEventSyncService;
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
@ConditionalOnProperty(name = "api-football.sync.fixture-events.enabled", havingValue = "true")
public class ApiFootballFixtureEventSyncScheduler {

    private final ApiFootballFixtureEventSyncService apiFootballFixtureEventSyncService;
    private final FixtureRecordRepository fixtureRecordRepository;

    @Scheduled(cron = "${api-football.sync.fixture-events.live-cron:0 * * * * *}")
    public void syncLiveFixtureEvents() {
        syncFixtures(fixtureRecordRepository.findAllByFixtureStatus("LIVE"), "live");
    }

    @Scheduled(cron = "${api-football.sync.fixture-events.daily-cron:0 30 4 * * *}")
    public void syncNonLiveFixtureEventsDaily() {
        syncFixtures(fixtureRecordRepository.findAllByFixtureStatusNot("LIVE"), "daily");
    }

    private void syncFixtures(List<Fixture> fixtures, String reason) {
        for (Fixture fixture : fixtures) {
            try {
                apiFootballFixtureEventSyncService.syncEvents(fixture.getFixtureId());
            } catch (Exception e) {
                log.error("API-Football fixture event sync failed. reason={}, fixtureId={}",
                        reason, fixture.getFixtureId(), e);
            }
        }
    }
}
