package com.son.soccerStreaming.apifootball.service;

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
@Order(2)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "api-football.sync.fixtures.run-on-startup", havingValue = "true")
public class ApiFootballFixtureStartupSyncRunner implements CommandLineRunner {

    private final ApiFootballFixtureSyncService apiFootballFixtureSyncService;

    @Value("${api-football.sync.fixtures.league:39}")
    private Integer league;

    @Value("${api-football.sync.fixtures.season:2024}")
    private Integer season;

    @Override
    public void run(String... args) {
        try {
            log.info("Sync fixture starting up...");
            apiFootballFixtureSyncService.syncSeasonFixtures(league, season);
        } catch (Exception e) {
            log.error("API-Football startup fixture sync failed. league={}, season={}", league, season, e);
        }
    }
}
