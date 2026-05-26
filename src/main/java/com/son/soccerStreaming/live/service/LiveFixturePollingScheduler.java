package com.son.soccerStreaming.live.service;

import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@ConditionalOnProperty(name = "live.sync.enabled", havingValue = "true")
public class LiveFixturePollingScheduler {

    private final LiveFixtureSyncService liveFixtureSyncService;
    private final SseService sseService;
    private final FixtureRepository fixtureRepository;
    private final Clock clock;
    private final Map<Long, FailureState> failureStates = new ConcurrentHashMap<>();
    private final int failureThreshold;
    private final long failureCooldownMs;

    @Autowired
    public LiveFixturePollingScheduler(
            LiveFixtureSyncService liveFixtureSyncService,
            SseService sseService,
            FixtureRepository fixtureRepository,
            @Value("${live.sync.failure-threshold:3}") int failureThreshold,
            @Value("${live.sync.failure-cooldown-ms:60000}") long failureCooldownMs
    ) {
        this(liveFixtureSyncService, sseService, fixtureRepository, Clock.systemUTC(),
                failureThreshold, failureCooldownMs);
    }

    LiveFixturePollingScheduler(
            LiveFixtureSyncService liveFixtureSyncService,
            SseService sseService,
            FixtureRepository fixtureRepository,
            Clock clock,
            int failureThreshold,
            long failureCooldownMs
    ) {
        this.liveFixtureSyncService = liveFixtureSyncService;
        this.sseService = sseService;
        this.fixtureRepository = fixtureRepository;
        this.clock = clock;
        this.failureThreshold = failureThreshold;
        this.failureCooldownMs = failureCooldownMs;
    }

    @Scheduled(fixedDelayString = "${live.sync.interval-ms:10000}")
    public void pollLiveFixtures() {
        // Poll only fixtures with active SSE subscribers to avoid unnecessary API calls.
        for (Long fixtureId : subscribedFixtureIds()) {
            long now = clock.millis();
            if (shouldSkip(fixtureId, now)) {
                continue;
            }
            if (!isLiveFixture(fixtureId)) {
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

    private List<Long> subscribedFixtureIds() {
        try {
            return sseService.getSubscribedFixtureIds();
        } catch (Exception e) {
            log.error("Failed to read SSE subscribed fixture ids. Skip this live polling tick.", e);
            return List.of();
        }
    }

    private boolean isLiveFixture(Long fixtureId) {
        try {
            return fixtureRepository.findByFixtureId(fixtureId)
                    .map(fixture -> "LIVE".equals(fixture.getFixtureStatus()))
                    .orElse(false);
        } catch (Exception e) {
            log.error("Failed to check live fixture status. fixtureId={}", fixtureId, e);
            return false;
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
