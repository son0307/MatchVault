package com.son.soccerStreaming.apifootball.runner;

import com.son.soccerStreaming.apifootball.service.ApiFootballFixtureDetailSyncService;
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
@Order(5)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "api-football.sync.fixture-details.run-on-startup", havingValue = "true")
public class ApiFootballFixtureDetailStartupSyncRunner implements CommandLineRunner {

    private final ApiFootballFixtureDetailSyncService apiFootballFixtureDetailSyncService;

    @Value("${api-football.sync.fixtures.season:2024}")
    private Integer season;

    @Override
    public void run(String... args) {
        log.info("API-Football startup fixture detail sync started. season={}", season);
        int syncedCount = apiFootballFixtureDetailSyncService.syncSeasonFixtureDetails(season, false);
        log.info("API-Football startup fixture detail sync completed. season={}, count={}", season, syncedCount);
    }
}
