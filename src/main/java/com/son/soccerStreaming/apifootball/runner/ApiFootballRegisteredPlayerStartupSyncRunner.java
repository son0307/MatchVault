package com.son.soccerStreaming.apifootball.runner;

import com.son.soccerStreaming.apifootball.service.ApiFootballPlayerSyncService;
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
@Order(4)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "api-football.sync.players.registered.run-on-startup", havingValue = "true")
public class ApiFootballRegisteredPlayerStartupSyncRunner implements CommandLineRunner {

    private final ApiFootballPlayerSyncService apiFootballPlayerSyncService;

    @Value("${api-football.sync.players.registered.league:39}")
    private Integer league;

    @Value("${api-football.sync.players.registered.season:2025}")
    private Integer season;

    @Value("${api-football.sync.players.registered.startup-delay-ms:7000}")
    private Long delayMs;

    @Override
    public void run(String... args) {
        log.info("API-Football startup registered player sync started. league={}, season={}", league, season);
        apiFootballPlayerSyncService.syncRegisteredPlayers(league, season, delayMs);
    }
}
