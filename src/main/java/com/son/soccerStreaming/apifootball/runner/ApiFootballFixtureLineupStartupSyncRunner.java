package com.son.soccerStreaming.apifootball.runner;

import com.son.soccerStreaming.apifootball.service.ApiFootballFixtureLineupSyncService;
import com.son.soccerStreaming.entity.Fixture;
import com.son.soccerStreaming.repository.FixtureRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("prod")
@Order(6)
@RequiredArgsConstructor
@ConditionalOnExpression("${api-football.sync.fixture-lineups.run-on-startup:false} && !${api-football.sync.fixture-details.enabled:false}")
public class ApiFootballFixtureLineupStartupSyncRunner implements CommandLineRunner {

    private final ApiFootballFixtureLineupSyncService apiFootballFixtureLineupSyncService;
    private final FixtureRecordRepository fixtureRecordRepository;

    @Value("${api-football.sync.fixture-lineups.startup-delay-ms:300}")
    private Long delayMs;

    @Override
    public void run(String... args) {
        log.info("API-Football startup fixture lineup sync started.");
        for (Fixture fixture : fixtureRecordRepository.findAllByOrderByFixtureDateAsc()) {
            try {
                apiFootballFixtureLineupSyncService.syncLineups(fixture.getFixtureId());
                sleepBetweenRequests();
            } catch (Exception e) {
                log.error("API-Football startup fixture lineup sync failed. fixtureId={}", fixture.getFixtureId(), e);
            }
        }
    }

    private void sleepBetweenRequests() {
        if (delayMs == null || delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
