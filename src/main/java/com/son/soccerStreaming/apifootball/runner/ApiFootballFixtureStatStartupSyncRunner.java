package com.son.soccerStreaming.apifootball.runner;

import com.son.soccerStreaming.apifootball.service.ApiFootballFixtureStatSyncService;
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
@Order(8)
@RequiredArgsConstructor
@ConditionalOnExpression("${api-football.sync.fixture-statistics.run-on-startup:false} && !${api-football.sync.fixture-details.enabled:false}")
public class ApiFootballFixtureStatStartupSyncRunner implements CommandLineRunner {

    private final ApiFootballFixtureStatSyncService apiFootballFixtureStatSyncService;
    private final FixtureRecordRepository fixtureRecordRepository;

    @Value("${api-football.sync.fixture-statistics.startup-delay-ms:300}")
    private Long delayMs;

    @Override
    public void run(String... args) {
        log.info("API-Football startup fixture stat sync started.");
        for (Fixture fixture : fixtureRecordRepository.findAllByOrderByFixtureDateAsc()) {
            try {
                apiFootballFixtureStatSyncService.syncFixtureStats(fixture.getFixtureId());
                sleepBetweenRequests();
            } catch (Exception e) {
                log.error("API-Football startup fixture stat sync failed. fixtureId={}", fixture.getFixtureId(), e);
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
