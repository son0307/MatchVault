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
@ConditionalOnProperty(name = "api-football.sync.standings.run-on-startup", havingValue = "true")
public class ApiFootballStandingStartupSyncRunner implements CommandLineRunner {

    private final ApiFootballStandingSyncService apiFootballStandingSyncService;

    @Value("${api-football.sync.standings.league:39}")
    private Integer league;

    @Value("${api-football.sync.standings.season:2024}")
    private Integer season;

    @Override
    public void run(String... args) {
        try {
            apiFootballStandingSyncService.syncStandings(league, season);
        } catch (Exception e) {
            log.error("API-Football startup standing sync failed. league={}, season={}", league, season, e);
        }
    }
}
