package com.son.soccerStreaming.apifootball.client;

import com.son.soccerStreaming.apifootball.service.ApiFootballSyncStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiFootballCircuitBreaker {

    private static final String GLOBAL_SYNC_KEY = "api-football";
    private static final String GLOBAL_DISPLAY_NAME = "API-Football";

    private final ApiFootballSyncStatusService syncStatusService;
    private final Clock clock = Clock.systemUTC();
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong openUntilEpochMs = new AtomicLong();

    @Value("${live.api-football.circuit-breaker.enabled:true}")
    private boolean enabled;

    @Value("${live.api-football.circuit-breaker.failure-threshold:5}")
    private int failureThreshold;

    @Value("${live.api-football.circuit-breaker.cooldown-ms:60000}")
    private long cooldownMs;

    public void beforeRequest(String operation) {
        if (!enabled) {
            return;
        }
        long now = clock.millis();
        long openUntil = openUntilEpochMs.get();
        if (openUntil > now) {
            throw new ApiFootballCircuitOpenException(
                    "API-Football circuit is open. operation=%s; retryAfterMs=%d"
                            .formatted(operation, openUntil - now)
            );
        }
    }

    public void recordSuccess() {
        if (!enabled) {
            return;
        }
        consecutiveFailures.set(0);
        openUntilEpochMs.set(0L);
        syncStatusService.recordSuccessByKey(GLOBAL_SYNC_KEY, GLOBAL_DISPLAY_NAME);
    }

    public void recordFailure(String operation, Exception exception) {
        if (!enabled || exception instanceof ApiFootballCircuitOpenException) {
            return;
        }
        int failures = consecutiveFailures.incrementAndGet();
        if (failures < configuredFailureThreshold()) {
            return;
        }

        long openUntil = clock.millis() + configuredCooldownMs();
        openUntilEpochMs.set(openUntil);
        log.warn("API-Football circuit opened. operation={}, failureCount={}, openUntilEpochMs={}",
                operation, failures, openUntil);
        syncStatusService.recordFailureByKey(GLOBAL_SYNC_KEY, GLOBAL_DISPLAY_NAME, exception);
    }

    private int configuredFailureThreshold() {
        return Math.max(1, failureThreshold);
    }

    private long configuredCooldownMs() {
        return Math.max(0L, cooldownMs);
    }
}
