package com.son.soccerStreaming.live.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@ConditionalOnProperty(name = "live.sync.enabled", havingValue = "true")
public class LiveFixturePollingScheduler {

    private final LiveFixtureSyncService liveFixtureSyncService;
    private final SseService sseService;
    private final Clock clock;
    private final Map<Long, FailureState> failureStates = new ConcurrentHashMap<>();
    private final int failureThreshold;
    private final long failureCooldownMs;

    public LiveFixturePollingScheduler(
            LiveFixtureSyncService liveFixtureSyncService,
            SseService sseService,
            @Value("${live.sync.failure-threshold:3}") int failureThreshold,
            @Value("${live.sync.failure-cooldown-ms:60000}") long failureCooldownMs
    ) {
        this(liveFixtureSyncService, sseService, Clock.systemUTC(), failureThreshold, failureCooldownMs);
    }

    LiveFixturePollingScheduler(
            LiveFixtureSyncService liveFixtureSyncService,
            SseService sseService,
            Clock clock,
            int failureThreshold,
            long failureCooldownMs
    ) {
        this.liveFixtureSyncService = liveFixtureSyncService;
        this.sseService = sseService;
        this.clock = clock;
        this.failureThreshold = failureThreshold;
        this.failureCooldownMs = failureCooldownMs;
    }

    @Scheduled(fixedDelayString = "${live.sync.interval-ms:10000}")
    public void pollLiveFixtures() {
        // Poll only fixtures with active SSE subscribers to avoid unnecessary API calls.
        for (Long fixtureId : sseService.getSubscribedFixtureIds()) {
            long now = clock.millis();
            if (shouldSkip(fixtureId, now)) {
                continue;
            }

            try {
                liveFixtureSyncService.syncFixture(fixtureId);
                failureStates.remove(fixtureId);
            } catch (Exception e) {
                FailureState failureState = recordFailure(fixtureId, now);
                log.error("Live fixture sync failed. fixtureId={}", fixtureId, e);
                if (failureState.failureCount() >= configuredFailureThreshold()) {
                    log.warn("Live fixture sync cooldown started. fixtureId={}, failureCount={}, cooldownUntilEpochMs={}",
                            fixtureId, failureState.failureCount(), failureState.cooldownUntilEpochMs());
                }
            }
        }
    }

    private boolean shouldSkip(Long fixtureId, long now) {
        FailureState failureState = failureStates.get(fixtureId);
        if (failureState == null || failureState.failureCount() < configuredFailureThreshold()) {
            return false;
        }

        if (now >= failureState.cooldownUntilEpochMs()) {
            return false;
        }

        log.warn("Live fixture sync skipped during cooldown. fixtureId={}, failureCount={}, cooldownUntilEpochMs={}",
                fixtureId, failureState.failureCount(), failureState.cooldownUntilEpochMs());
        return true;
    }

    private FailureState recordFailure(Long fixtureId, long now) {
        return failureStates.compute(fixtureId, (id, previous) -> {
            int failureCount = previous == null ? 1 : previous.failureCount() + 1;
            long cooldownUntilEpochMs = failureCount >= configuredFailureThreshold()
                    ? now + configuredFailureCooldownMs()
                    : 0L;
            return new FailureState(failureCount, cooldownUntilEpochMs);
        });
    }

    private int configuredFailureThreshold() {
        return Math.max(1, failureThreshold);
    }

    private long configuredFailureCooldownMs() {
        return Math.max(0L, failureCooldownMs);
    }

    private record FailureState(int failureCount, long cooldownUntilEpochMs) {
    }
}
