package com.son.soccerStreaming.apifootball.scheduler;

import com.son.soccerStreaming.apifootball.service.ApiFootballFixtureDetailSyncException;
import com.son.soccerStreaming.apifootball.service.ApiFootballFixtureDetailSyncService;
import com.son.soccerStreaming.live.service.LiveFixtureBroadcastService;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "api-football.sync.fixture-details.enabled", havingValue = "true")
public class ApiFootballFixtureDetailSyncScheduler {

    private final ApiFootballFixtureDetailSyncService apiFootballFixtureDetailSyncService;
    private final LiveFixtureBroadcastService liveFixtureBroadcastService;
    private final FixtureRepository fixtureRepository;
    private final ApiFootballSyncFailureRetryScheduler failureRetryScheduler;

    @Value("${api-football.sync.fixtures.season:2025}")
    private Integer season;

    @Scheduled(cron = "${api-football.sync.fixture-details.live-cron:0 * * * * *}")
    public void syncLiveFixtureDetails() {
        try {
            syncLiveFixtureDetailsNow();
        } catch (Exception e) {
            log.error("API-Football live fixture detail sync failed.", e);
            scheduleFixtureDetailRetry("live", true, e);
        }
    }

    @Scheduled(cron = "${api-football.sync.fixture-details.daily-cron:0 55 4 * * *}")
    public void syncFixtureDetailsDaily() {
        try {
            apiFootballFixtureDetailSyncService.syncSeasonFixtureDetails(season, false);
        } catch (Exception e) {
            log.error("API-Football daily fixture detail sync failed.", e);
            scheduleFixtureDetailRetry("daily:%s".formatted(season), false, e);
        }
    }

    private void syncLiveFixtureDetailsNow() {
        apiFootballFixtureDetailSyncService.syncFixtureDetailsWithResults(
                fixtureRepository.findAllByFixtureStatus("LIVE"),
                true
        ).forEach(result -> liveFixtureBroadcastService.broadcastFixture(result.fixtureId(), result.latestEvent()));
    }

    private void scheduleFixtureDetailRetry(String reason, boolean applyLiveStandingImpact, Exception exception) {
        if (exception instanceof ApiFootballFixtureDetailSyncException fixtureDetailException) {
            int index = 1;
            for (java.util.List<Long> chunk : fixtureDetailException.getFailedChunks()) {
                String fixtureIds = chunk.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining("-"));
                failureRetryScheduler.schedule(
                        "fixture-details:%s:chunk:%s".formatted(reason, fixtureIds),
                        "fixture detail sync reason=%s chunk=%s".formatted(reason, index++),
                        () -> apiFootballFixtureDetailSyncService.syncFixtureDetailsByIds(chunk, applyLiveStandingImpact)
                );
            }
            return;
        }

        failureRetryScheduler.schedule(
                "fixture-details:%s".formatted(reason),
                "fixture detail sync reason=%s".formatted(reason),
                applyLiveStandingImpact ? this::syncLiveFixtureDetailsNow
                        : () -> apiFootballFixtureDetailSyncService.syncSeasonFixtureDetails(season, false)
        );
    }
}
