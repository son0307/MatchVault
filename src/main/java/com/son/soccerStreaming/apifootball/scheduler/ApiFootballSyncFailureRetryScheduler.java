package com.son.soccerStreaming.apifootball.scheduler;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
                log.error("API-Football sync failure retry exhausted. alert=api-football-sync-retry-exhausted, key={}, description={}, attempts={}",
                        key, state.description(), attempt, e);
                return;
            }

            log.error("API-Football sync failure retry failed. key={}, description={}, attempt={}/{}",
                    key, state.description(), attempt, state.maxAttempts(), e);
            scheduleNext(key, state);
        }
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
