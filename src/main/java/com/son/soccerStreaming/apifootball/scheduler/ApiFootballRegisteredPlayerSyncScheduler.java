package com.son.soccerStreaming.apifootball.scheduler;

import com.son.soccerStreaming.apifootball.service.ApiFootballPlayerSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballRegisteredPlayerSyncException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "api-football.sync.players.registered.enabled", havingValue = "true")
public class ApiFootballRegisteredPlayerSyncScheduler {

    private final ApiFootballPlayerSyncService apiFootballPlayerSyncService;
    private final ApiFootballSyncFailureRetryScheduler failureRetryScheduler;

    @Value("${api-football.sync.players.registered.league:39}")
    private Integer league;

    @Value("${api-football.sync.players.registered.season:2025}")
    private Integer season;

    @Value("${api-football.sync.players.registered.delay-ms:7000}")
    private Long delayMs;

    @Scheduled(cron = "${api-football.sync.players.registered.daily-cron:0 10 5 * * *}")
    public void syncRegisteredPlayersDaily() {
        try {
            apiFootballPlayerSyncService.syncRegisteredPlayers(league, season, delayMs);
        } catch (Exception e) {
            log.error("API-Football registered player sync failed. league={}, season={}", league, season, e);
            scheduleRetry(e);
        }
    }

    private void scheduleRetry(Exception exception) {
        if (exception instanceof ApiFootballRegisteredPlayerSyncException playerSyncException) {
            for (Long teamId : playerSyncException.getFailedTeamIds()) {
                failureRetryScheduler.schedule(
                        "registered-players:%s:%s:team:%s".formatted(league, season, teamId),
                        "registered player sync league=%s season=%s teamId=%s".formatted(league, season, teamId),
                        () -> apiFootballPlayerSyncService.syncRegisteredPlayersByTeamId(teamId, league, season, delayMs)
                );
            }
            return;
        }

        failureRetryScheduler.schedule(
                "registered-players:%s:%s".formatted(league, season),
                "registered player sync league=%s season=%s".formatted(league, season),
                () -> apiFootballPlayerSyncService.syncRegisteredPlayers(league, season, delayMs)
        );
    }
}
