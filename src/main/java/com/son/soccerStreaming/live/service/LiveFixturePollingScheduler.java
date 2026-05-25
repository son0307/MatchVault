package com.son.soccerStreaming.live.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "live.sync.enabled", havingValue = "true")
public class LiveFixturePollingScheduler {

    private final LiveFixtureSyncService liveFixtureSyncService;
    private final SseService sseService;

    @Scheduled(fixedDelayString = "${live.sync.interval-ms:10000}")
    public void pollLiveFixtures() {
        // Poll only fixtures with active SSE subscribers to avoid unnecessary API calls.
        for (Long fixtureId : sseService.getSubscribedFixtureIds()) {
            try {
                liveFixtureSyncService.syncFixture(fixtureId);
            } catch (Exception e) {
                log.error("Live fixture sync failed. fixtureId={}", fixtureId, e);
            }
        }
    }
}
