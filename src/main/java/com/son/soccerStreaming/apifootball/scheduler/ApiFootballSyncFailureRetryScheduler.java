package com.son.soccerStreaming.apifootball.scheduler;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.son.soccerStreaming.apifootball.service.ApiFootballSyncStatusService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiFootballSyncFailureRetryScheduler {

    private final ApiFootballSyncStatusService syncStatusService;
    private final ScheduledExecutorService retryExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "api-football-sync-failure-retry");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, RetryState> retryStates = new ConcurrentHashMap<>();

    @Value("${api-football.sync.failure-retry.enabled:true}")
    private boolean enabled;

    @Value("${api-football.sync.failure-retry.max-attempts:2}")
    private int maxAttempts;

    @Value("${api-football.sync.failure-retry.delay-minutes:30}")
    private long delayMinutes;

    public void schedule(String key, String description, Runnable retryAction) {
        if (!enabled) {
            log.warn("API-Football sync failure retry skipped because it is disabled. key={}, description={}",
                    key, description);
            return;
        }

        int configuredMaxAttempts = Math.max(0, maxAttempts);
        if (configuredMaxAttempts == 0) {
            log.error("API-Football sync failure retry exhausted. alert=api-football-sync-retry-exhausted, key={}, description={}, maxAttempts=0",
                    key, description);
            return;
        }

        RetryState newState = new RetryState(description, retryAction, configuredMaxAttempts);
        RetryState existingState = retryStates.putIfAbsent(key, newState);
        if (existingState != null) {
            log.warn("API-Football sync failure retry already pending. key={}, description={}, currentAttempt={}/{}",
                    key, existingState.description(), existingState.attempt(), existingState.maxAttempts());
            return;
        }

        syncStatusOfRetryKey(key).ifPresent(status ->
                syncStatusService.recordRetryPendingByKey(status.syncKey(), status.displayName(),
                        "Retry scheduled. " + description));
        scheduleNext(key, newState);
    }

    @PreDestroy
    public void shutdown() {
        retryExecutor.shutdownNow();
    }

    private void scheduleNext(String key, RetryState state) {
        long delay = Math.max(0, delayMinutes);
        int nextAttempt = state.attempt() + 1;
        log.warn("API-Football sync failure retry scheduled. key={}, description={}, attempt={}/{}, delayMinutes={}",
                key, state.description(), nextAttempt, state.maxAttempts(), delay);

        retryExecutor.schedule(() -> runRetry(key), delay, TimeUnit.MINUTES);
    }

    private void runRetry(String key) {
        RetryState state = retryStates.get(key);
        if (state == null) {
            return;
        }

        int attempt = state.incrementAttempt();
        try {
            log.info("API-Football sync failure retry started. key={}, description={}, attempt={}/{}",
                    key, state.description(), attempt, state.maxAttempts());
            state.retryAction().run();
            retryStates.remove(key);
            log.info("API-Football sync failure retry succeeded. key={}, description={}, attempt={}/{}",
                    key, state.description(), attempt, state.maxAttempts());
        } catch (Exception e) {
            if (attempt >= state.maxAttempts()) {
                retryStates.remove(key);
                syncStatusOfRetryKey(key).ifPresent(status ->
                        syncStatusService.recordFailureByKey(status.syncKey(), status.displayName(), e));
                log.error("API-Football sync failure retry exhausted. alert=api-football-sync-retry-exhausted, key={}, description={}, attempts={}",
                        key, state.description(), attempt, e);
                return;
            }

            log.error("API-Football sync failure retry failed. key={}, description={}, attempt={}/{}",
                    key, state.description(), attempt, state.maxAttempts(), e);
            syncStatusOfRetryKey(key).ifPresent(status ->
                    syncStatusService.recordRetryPendingByKey(status.syncKey(), status.displayName(),
                            "Retry failed and was rescheduled. " + state.description()));
            scheduleNext(key, state);
        }
    }

    private java.util.Optional<SyncStatusTarget> syncStatusOfRetryKey(String key) {
        String[] parts = key.split(":");
        if (parts.length == 0) {
            return java.util.Optional.empty();
        }
        return switch (parts[0]) {
            case "teams" -> seasonTarget("teams", "Teams", parts, 2);
            case "standings" -> seasonTarget("standings", "Standings", parts, parts.length - 1);
            case "fixtures" -> seasonTarget("fixtures", "Fixtures", parts, parts.length - 1);
            case "fixture-details" -> seasonTarget("fixture-details", "Season Details", parts, 1);
            case "registered-players" -> seasonTarget("players", "Players", parts, 2);
            case "injuries" -> seasonTarget("injuries", "Injuries", parts, 2);
            case "league-seasons" -> parts.length > 1
                    ? java.util.Optional.of(new SyncStatusTarget("league-seasons:" + parts[1], "League Seasons"))
                    : java.util.Optional.empty();
            case "startup" -> startupTarget(parts);
            default -> java.util.Optional.empty();
        };
    }

    private java.util.Optional<SyncStatusTarget> startupTarget(String[] parts) {
        if (parts.length < 3) {
            return java.util.Optional.empty();
        }
        return switch (parts[1]) {
            case "fixture-details" -> seasonTarget("fixture-details", "Season Details", parts, 2);
            case "registered-players" -> seasonTarget("players", "Players", parts, 3);
            default -> java.util.Optional.empty();
        };
    }

    private java.util.Optional<SyncStatusTarget> seasonTarget(String task, String displayName, String[] parts, int seasonIndex) {
        if (seasonIndex < 0 || seasonIndex >= parts.length) {
            return java.util.Optional.empty();
        }
        try {
            int season = Integer.parseInt(parts[seasonIndex]);
            return java.util.Optional.of(new SyncStatusTarget("%s:%d".formatted(task, season), "%s %d".formatted(displayName, season)));
        } catch (NumberFormatException exception) {
            return java.util.Optional.empty();
        }
    }

    private record SyncStatusTarget(String syncKey, String displayName) {
    }

    private static class RetryState {

        private static final java.util.concurrent.atomic.AtomicIntegerFieldUpdater<RetryState> ATTEMPT_UPDATER =
                java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater(RetryState.class, "attempt");

        private final String description;
        private final Runnable retryAction;
        private final int maxAttempts;

        @SuppressWarnings("unused")
        private volatile int attempt;

        private RetryState(String description, Runnable retryAction, int maxAttempts) {
            this.description = description;
            this.retryAction = retryAction;
            this.maxAttempts = maxAttempts;
        }

        String description() {
            return description;
        }

        Runnable retryAction() {
            return retryAction;
        }

        int maxAttempts() {
            return maxAttempts;
        }

        int attempt() {
            return attempt;
        }

        int incrementAttempt() {
            return ATTEMPT_UPDATER.incrementAndGet(this);
        }
    }
}
