package com.son.soccerStreaming.apifootball.scheduler;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import com.son.soccerStreaming.apifootball.service.ApiFootballSyncStatusService;
import com.son.soccerStreaming.apifootball.service.ApiFootballSyncExecutionGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReference;
import com.son.soccerStreaming.global.externalapi.ExternalApiException;

@Slf4j
@Component
public class ApiFootballSyncFailureRetryScheduler {

    private final ApiFootballSyncStatusService syncStatusService;
    private final ApiFootballSyncExecutionGuard executionGuard;
    private final ScheduledExecutorService retryExecutor;
    private final Map<String, RetryState> retryStates = new ConcurrentHashMap<>();
    private final Map<String, String> terminalFailedRetries = new ConcurrentHashMap<>();

    @Value("${api-football.sync.failure-retry.enabled:true}")
    private boolean enabled;

    @Value("${api-football.sync.failure-retry.max-attempts:2}")
    private int maxAttempts;

    @Value("${api-football.sync.failure-retry.initial-delay-minutes:1}")
    private long initialDelayMinutes;

    @Value("${api-football.sync.failure-retry.delay-multiplier:5}")
    private long delayMultiplier;

    @Value("${api-football.sync.failure-retry.max-delay-minutes:30}")
    private long maxDelayMinutes;

    @Autowired
    public ApiFootballSyncFailureRetryScheduler(ApiFootballSyncStatusService syncStatusService,
                                                ApiFootballSyncExecutionGuard executionGuard) {
        this(syncStatusService, executionGuard, newRetryExecutor());
    }

    ApiFootballSyncFailureRetryScheduler(ApiFootballSyncStatusService syncStatusService,
                                         ApiFootballSyncExecutionGuard executionGuard,
                                         ScheduledExecutorService retryExecutor) {
        this.syncStatusService = syncStatusService;
        this.executionGuard = executionGuard;
        this.retryExecutor = retryExecutor;
    }

    private static ScheduledExecutorService newRetryExecutor() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "api-football-sync-failure-retry");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void schedule(String retryKey, String executionKey, String description,
                         Exception failure, Runnable retryAction) {
        if (!enabled) {
            log.warn("API-Football sync failure retry skipped because it is disabled. retryKey={}, executionKey={}, description={}",
                    retryKey, executionKey, description);
            return;
        }

        int configuredMaxAttempts = Math.max(0, maxAttempts);
        if (configuredMaxAttempts == 0) {
            log.error("API-Football sync failure retry exhausted. alert=api-football-sync-retry-exhausted, retryKey={}, executionKey={}, description={}, maxAttempts=0",
                    retryKey, executionKey, description);
            return;
        }

        RetryState newState = new RetryState(executionKey, description, retryAction, configuredMaxAttempts);
        // A new automatic/startup synchronization supersedes terminal failures from its previous run.
        // Partial retries use scheduleNext directly, so they do not clear sibling terminal failures here.
        terminalFailedRetries.entrySet().removeIf(entry -> entry.getValue().equals(executionKey));
        RetryState existingState = retryStates.putIfAbsent(retryKey, newState);
        if (existingState != null) {
            log.warn("API-Football sync failure retry already pending. retryKey={}, executionKey={}, description={}, currentAttempt={}/{}",
                    retryKey, existingState.executionKey(), existingState.description(),
                    existingState.attempt(), existingState.maxAttempts());
            return;
        }

        scheduleNext(retryKey, newState, failure, false);
    }

    public boolean shouldRetry(Exception exception) {
        return externalApiException(exception)
                .map(ExternalApiException::isRetryable)
                .orElse(true);
    }

    public int cancelPendingByExecutionKey(String executionKey) {
        int cancelledCount = 0;
        for (Map.Entry<String, RetryState> entry : retryStates.entrySet()) {
            RetryState state = entry.getValue();
            if (!state.executionKey().equals(executionKey)) {
                continue;
            }
            if (retryStates.remove(entry.getKey(), state)) {
                state.cancel();
                cancelledCount++;
                log.info("API-Football pending retry cancelled after synchronization success. retryKey={}, executionKey={}, attempt={}/{}",
                        entry.getKey(), executionKey, state.attempt(), state.maxAttempts());
            }
        }
        terminalFailedRetries.entrySet().removeIf(entry -> entry.getValue().equals(executionKey));
        return cancelledCount;
    }

    @PreDestroy
    public void shutdown() {
        retryStates.values().forEach(RetryState::cancel);
        retryStates.clear();
        terminalFailedRetries.clear();
        retryExecutor.shutdownNow();
    }

    private void scheduleNext(String retryKey, RetryState state, Exception failure, boolean activeJobDeferred) {
        if (!isCurrent(retryKey, state)) {
            return;
        }

        Duration delay = activeJobDeferred
                ? Duration.ofMinutes(Math.max(0, initialDelayMinutes))
                : retryDelay(state.attempt(), failure);
        long delayMillis = safeMillis(delay);
        int nextAttempt = state.attempt() + 1;
        Instant nextAttemptAt = Instant.now().plusMillis(delayMillis);
        boolean retryAfterApplied = !activeJobDeferred && retryAfter(failure).isPresent();

        log.atWarn()
                .addKeyValue("event.action", "api-football-sync-retry")
                .addKeyValue("event.outcome", "scheduled")
                .addKeyValue("api_football.retry_key", retryKey)
                .addKeyValue("api_football.execution_key", state.executionKey())
                .addKeyValue("api_football.retry_attempt", nextAttempt)
                .addKeyValue("api_football.retry_max_attempts", state.maxAttempts())
                .addKeyValue("api_football.retry_delay_ms", delayMillis)
                .addKeyValue("api_football.retry_at", nextAttemptAt)
                .addKeyValue("api_football.retry_after_applied", retryAfterApplied)
                .log("API-Football sync failure retry scheduled.");

        if (!activeJobDeferred) {
            syncStatusOfRetryKey(retryKey).ifPresent(status ->
                    syncStatusService.recordRetryPendingByKey(status.syncKey(), status.displayName(),
                            "Retry scheduled for %s (attempt %d/%d, delay=%dms, retryAfter=%s). %s"
                                    .formatted(nextAttemptAt, nextAttempt, state.maxAttempts(), delayMillis,
                                            retryAfterApplied, state.description())));
        }

        ScheduledFuture<?> future = retryExecutor.schedule(
                () -> runRetry(retryKey, state), delayMillis, TimeUnit.MILLISECONDS);
        state.replaceScheduledFuture(future);
        if (!isCurrent(retryKey, state)) {
            state.cancel();
        }
    }

    private void runRetry(String retryKey, RetryState state) {
        if (!isCurrent(retryKey, state)) {
            return;
        }

        if (!executionGuard.executeIfAvailable(state.executionKey(), () -> runRetryNow(retryKey, state))) {
            if (!isCurrent(retryKey, state)) {
                return;
            }
            log.info("API-Football retry deferred because the same synchronization is active. retryKey={}, executionKey={}",
                    retryKey, state.executionKey());
            scheduleNext(retryKey, state, null, true);
        }
    }

    private void runRetryNow(String retryKey, RetryState state) {
        if (!isCurrent(retryKey, state)) {
            return;
        }

        int attempt = state.incrementAttempt();
        try {
            log.info("API-Football sync failure retry started. retryKey={}, executionKey={}, description={}, attempt={}/{}",
                    retryKey, state.executionKey(), state.description(), attempt, state.maxAttempts());
            state.retryAction().run();
            if (retryStates.remove(retryKey, state)) {
                state.cancel();
                recordSuccessWhenNoRelatedRetryRemains(retryKey);
            }
            log.info("API-Football sync failure retry succeeded. retryKey={}, executionKey={}, description={}, attempt={}/{}",
                    retryKey, state.executionKey(), state.description(), attempt, state.maxAttempts());
        } catch (Exception exception) {
            if (!isCurrent(retryKey, state)) {
                return;
            }
            if (!shouldRetry(exception)) {
                removeAndRecordFailure(retryKey, state, exception);
                log.error("API-Football sync failure retry stopped for a non-retryable error. retryKey={}, executionKey={}, description={}, attempt={}/{}",
                        retryKey, state.executionKey(), state.description(), attempt, state.maxAttempts(), exception);
                return;
            }
            if (attempt >= state.maxAttempts()) {
                removeAndRecordFailure(retryKey, state, exception);
                log.error("API-Football sync failure retry exhausted. alert=api-football-sync-retry-exhausted, retryKey={}, executionKey={}, description={}, attempts={}",
                        retryKey, state.executionKey(), state.description(), attempt, exception);
                return;
            }

            log.error("API-Football sync failure retry failed. retryKey={}, executionKey={}, description={}, attempt={}/{}",
                    retryKey, state.executionKey(), state.description(), attempt, state.maxAttempts(), exception);
            scheduleNext(retryKey, state, exception, false);
        }
    }

    private void removeAndRecordFailure(String retryKey, RetryState state, Exception exception) {
        if (retryStates.remove(retryKey, state)) {
            state.cancel();
        }
        terminalFailedRetries.put(retryKey, state.executionKey());
        syncStatusOfRetryKey(retryKey).ifPresent(status ->
                syncStatusService.recordFailureByKey(status.syncKey(), status.displayName(), exception));
    }

    private void recordSuccessWhenNoRelatedRetryRemains(String completedRetryKey) {
        if (!completedRetryKey.contains(":team:") && !completedRetryKey.contains(":chunk:")) {
            return;
        }
        Optional<SyncStatusTarget> completedTarget = syncStatusOfRetryKey(completedRetryKey);
        if (completedTarget.isEmpty()) {
            return;
        }
        SyncStatusTarget target = completedTarget.get();
        boolean relatedRetryRemains = retryStates.keySet().stream()
                .map(this::syncStatusOfRetryKey)
                .flatMap(Optional::stream)
                .anyMatch(target::equals);
        boolean relatedRetryFailed = terminalFailedRetries.keySet().stream()
                .map(this::syncStatusOfRetryKey)
                .flatMap(Optional::stream)
                .anyMatch(target::equals);
        if (!relatedRetryRemains && !relatedRetryFailed) {
            syncStatusService.recordSuccessByKey(target.syncKey(), target.displayName());
        }
    }

    Duration retryDelay(int completedAttempts, Exception failure) {
        Optional<Duration> retryAfter = retryAfter(failure);
        if (retryAfter.isPresent()) {
            return retryAfter.get();
        }

        long initial = Math.max(0, initialDelayMinutes);
        long maximum = Math.max(initial, maxDelayMinutes);
        long multiplier = Math.max(1, delayMultiplier);
        long delay = initial;
        for (int index = 0; index < completedAttempts && delay < maximum; index++) {
            if (delay > maximum / multiplier) {
                delay = maximum;
            } else {
                delay = Math.min(maximum, delay * multiplier);
            }
        }
        return Duration.ofMinutes(delay);
    }

    private Optional<Duration> retryAfter(Exception exception) {
        return externalApiException(exception)
                .map(ExternalApiException::getRetryAfter)
                .filter(delay -> !delay.isNegative());
    }

    private Optional<ExternalApiException> externalApiException(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ExternalApiException externalApiException) {
                return Optional.of(externalApiException);
            }
            current = current.getCause();
        }
        return Optional.empty();
    }

    private boolean isCurrent(String retryKey, RetryState state) {
        return !state.isCancelled() && retryStates.get(retryKey) == state;
    }

    private long safeMillis(Duration duration) {
        try {
            return Math.max(0, duration.toMillis());
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    int pendingRetryCount() {
        return retryStates.size();
    }

    private Optional<SyncStatusTarget> syncStatusOfRetryKey(String key) {
        String[] parts = key.split(":");
        if (parts.length == 0) {
            return Optional.empty();
        }
        return switch (parts[0]) {
            case "teams" -> seasonTarget("teams", "Teams", parts, 2);
            case "standings" -> seasonTarget("standings", "Standings", parts, parts.length - 1);
            case "fixtures" -> seasonTarget("fixtures", "Fixtures", parts, parts.length - 1);
            case "fixture-details" -> parts.length > 2 && "daily".equals(parts[1])
                    ? seasonTarget("fixture-details", "Season Details", parts, 2)
                    : Optional.empty();
            case "registered-players" -> seasonTarget("players", "Players", parts, 2);
            case "injuries" -> seasonTarget("injuries", "Injuries", parts, 2);
            case "league-seasons" -> parts.length > 1
                    ? Optional.of(new SyncStatusTarget("league-seasons:" + parts[1], "League Seasons"))
                    : Optional.empty();
            case "startup" -> startupTarget(parts);
            default -> Optional.empty();
        };
    }

    private Optional<SyncStatusTarget> startupTarget(String[] parts) {
        if (parts.length < 3) {
            return Optional.empty();
        }
        return switch (parts[1]) {
            case "league-seasons" -> Optional.of(new SyncStatusTarget("league-seasons:" + parts[2], "League Seasons"));
            case "teams" -> seasonTarget("teams", "Teams", parts, 3);
            case "standings" -> seasonTarget("standings", "Standings", parts, 3);
            case "fixtures" -> seasonTarget("fixtures", "Fixtures", parts, 3);
            case "fixture-details" -> seasonTarget("fixture-details", "Season Details", parts, 2);
            case "registered-players" -> seasonTarget("players", "Players", parts, 3);
            case "injuries" -> seasonTarget("injuries", "Injuries", parts, 3);
            default -> Optional.empty();
        };
    }

    private Optional<SyncStatusTarget> seasonTarget(String task, String displayName, String[] parts, int seasonIndex) {
        if (seasonIndex < 0 || seasonIndex >= parts.length) {
            return Optional.empty();
        }
        try {
            int season = Integer.parseInt(parts[seasonIndex]);
            return Optional.of(new SyncStatusTarget("%s:%d".formatted(task, season), "%s %d".formatted(displayName, season)));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private record SyncStatusTarget(String syncKey, String displayName) {
    }

    private static class RetryState {

        private static final AtomicIntegerFieldUpdater<RetryState> ATTEMPT_UPDATER =
                AtomicIntegerFieldUpdater.newUpdater(RetryState.class, "attempt");

        private final String executionKey;
        private final String description;
        private final Runnable retryAction;
        private final int maxAttempts;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicReference<ScheduledFuture<?>> scheduledFuture = new AtomicReference<>();

        @SuppressWarnings("unused")
        private volatile int attempt;

        private RetryState(String executionKey, String description, Runnable retryAction, int maxAttempts) {
            this.executionKey = executionKey;
            this.description = description;
            this.retryAction = retryAction;
            this.maxAttempts = maxAttempts;
        }

        String executionKey() {
            return executionKey;
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

        boolean isCancelled() {
            return cancelled.get();
        }

        void replaceScheduledFuture(ScheduledFuture<?> future) {
            ScheduledFuture<?> previous = scheduledFuture.getAndSet(future);
            if (previous != null && !previous.isDone()) {
                previous.cancel(false);
            }
            if (cancelled.get()) {
                future.cancel(false);
            }
        }

        void cancel() {
            cancelled.set(true);
            ScheduledFuture<?> future = scheduledFuture.getAndSet(null);
            if (future != null) {
                future.cancel(false);
            }
        }
    }
}
