package com.son.soccerStreaming.apifootball.runner;

import com.son.soccerStreaming.apifootball.scheduler.ApiFootballSyncFailureRetryScheduler;
import com.son.soccerStreaming.apifootball.service.ApiFootballFixtureDetailSyncException;
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
@Order(4)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "api-football.sync.fixture-details.run-on-startup", havingValue = "true")
public class ApiFootballFixtureDetailStartupSyncRunner implements CommandLineRunner {

    private final ApiFootballFixtureDetailSyncService apiFootballFixtureDetailSyncService;
    private final ApiFootballSyncFailureRetryScheduler failureRetryScheduler;

    @Value("${api-football.sync.fixtures.season:2025}")
    private Integer season;

    @Override
    public void run(String... args) {
        log.info("API-Football startup fixture detail sync started. season={}", season);
        try {
            int syncedCount = apiFootballFixtureDetailSyncService.syncSeasonFixtureDetails(season, false);
            log.info("API-Football startup fixture detail sync completed. season={}, count={}", season, syncedCount);
        } catch (Exception e) {
            log.error("API-Football startup fixture detail sync failed. season={}", season, e);
            scheduleRetry(e);
        }
    }

    private void scheduleRetry(Exception exception) {
        if (!failureRetryScheduler.shouldRetry(exception)) {
            return;
        }
        if (exception instanceof ApiFootballFixtureDetailSyncException fixtureDetailException) {
            int index = 1;
            for (java.util.List<Long> chunk : fixtureDetailException.getFailedChunks()) {
                String fixtureIds = chunk.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining("-"));
                failureRetryScheduler.schedule(
                        "startup:fixture-details:%s:chunk:%s".formatted(season, fixtureIds),
                        "startup fixture detail sync season=%s chunk=%s".formatted(season, index++),
                        () -> apiFootballFixtureDetailSyncService.syncFixtureDetailsByIds(chunk, false)
                );
            }
            return;
        }

        failureRetryScheduler.schedule(
                "startup:fixture-details:%s".formatted(season),
                "startup fixture detail sync season=%s".formatted(season),
                () -> apiFootballFixtureDetailSyncService.syncSeasonFixtureDetails(season, false)
        );
    }
}
