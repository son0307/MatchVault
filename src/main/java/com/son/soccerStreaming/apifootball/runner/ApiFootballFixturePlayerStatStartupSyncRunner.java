package com.son.soccerStreaming.apifootball.runner;

import com.son.soccerStreaming.apifootball.service.ApiFootballFixturePlayerStatSyncService;
import com.son.soccerStreaming.entity.Fixture;
import com.son.soccerStreaming.repository.FixtureRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("prod")
@Order(6)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "api-football.sync.fixture-player-stats.run-on-startup", havingValue = "true")
public class ApiFootballFixturePlayerStatStartupSyncRunner implements CommandLineRunner {

    private final ApiFootballFixturePlayerStatSyncService apiFootballFixturePlayerStatSyncService;
    private final FixtureRecordRepository fixtureRecordRepository;

    @Value("${api-football.sync.fixture-player-stats.startup-delay-ms:300}")
    private Long delayMs;

    @Override
    public void run(String... args) {
        log.info("API-Football startup fixture player stat sync started.");
        for (Fixture fixture : fixtureRecordRepository.findAllByOrderByFixtureDateAsc()) {
            try {
                apiFootballFixturePlayerStatSyncService.syncPlayerStats(fixture.getFixtureId());
                sleepBetweenRequests();
            } catch (Exception e) {
                log.error("API-Football startup fixture player stat sync failed. fixtureId={}", fixture.getFixtureId(), e);
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
