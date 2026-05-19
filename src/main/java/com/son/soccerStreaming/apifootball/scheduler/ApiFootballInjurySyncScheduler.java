package com.son.soccerStreaming.apifootball.scheduler;

import com.son.soccerStreaming.apifootball.service.ApiFootballInjurySyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "api-football.sync.injuries.enabled", havingValue = "true")
public class ApiFootballInjurySyncScheduler {

    private final ApiFootballInjurySyncService apiFootballInjurySyncService;

    @Value("${api-football.sync.injuries.league:39}")
    private Integer league;

    @Value("${api-football.sync.injuries.season:2025}")
    private Integer season;

    @Scheduled(cron = "${api-football.sync.injuries.daily-cron:0 0 5 * * *}")
    public void syncInjuriesDaily() {
        try {
            apiFootballInjurySyncService.syncInjuries(league, season);
        } catch (Exception e) {
            log.error("API-Football injury sync failed. league={}, season={}", league, season, e);
        }
    }
}
