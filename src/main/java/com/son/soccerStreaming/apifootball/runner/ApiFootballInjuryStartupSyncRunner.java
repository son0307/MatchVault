package com.son.soccerStreaming.apifootball.runner;

import com.son.soccerStreaming.apifootball.service.ApiFootballInjurySyncService;
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
@Order(9)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "api-football.sync.injuries.run-on-startup", havingValue = "true")
public class ApiFootballInjuryStartupSyncRunner implements CommandLineRunner {

    private final ApiFootballInjurySyncService apiFootballInjurySyncService;

    @Value("${api-football.sync.injuries.league:39}")
    private Integer league;

    @Value("${api-football.sync.injuries.season:2025}")
    private Integer season;

    @Override
    public void run(String... args) {
        log.info("API-Football startup injury sync started.");
        try {
            apiFootballInjurySyncService.syncInjuries(league, season);
        } catch (Exception e) {
            log.error("API-Football startup injury sync failed. league={}, season={}", league, season, e);
        }
    }
}
