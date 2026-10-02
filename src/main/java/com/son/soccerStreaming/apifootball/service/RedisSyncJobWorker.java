package com.son.soccerStreaming.apifootball.service;

import tools.jackson.databind.ObjectMapper;
import com.son.soccerStreaming.admin.entity.AdminSyncJob;
import com.son.soccerStreaming.admin.entity.AdminSyncJobStatus;
import com.son.soccerStreaming.admin.service.AdminSyncJobService;
import com.son.soccerStreaming.admin.service.AdminSyncTaskRunner;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import com.son.soccerStreaming.live.service.LiveFixtureBroadcastService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "api-football.sync.redis-queue.enabled", havingValue = "true")
public class RedisSyncJobWorker implements SmartLifecycle {
    static final String ACTIVE_WORKER_KEY = "redis-sync-worker:active";
    private static final Duration CLAIM_IDLE = Duration.ofMinutes(2);
    private static final Duration SHUTDOWN_WAIT = Duration.ofMinutes(30);
    private static final long MAX_FAILURES = 3;

    private final RedisSyncJobStream stream;
    private final SyncJobPublisher publisher;
    private final ObjectMapper objectMapper;
    private final ApiFootballSyncExecutionGuard guard;
    private final AdminSyncJobService adminJobs;
    private final AdminSyncTaskRunner adminRunner;
    private final ApiFootballSyncStatusService syncStatus;
    private final LeagueSeasonCoverageSyncService seasons;
    private final ApiFootballTeamSyncService teams;
    private final ApiFootballStandingSyncService standings;
    private final ApiFootballFixtureSyncService fixtures;
    private final ApiFootballFixtureDetailSyncService details;
    private final ApiFootballPlayerSyncService players;
    private final ApiFootballInjurySyncService injuries;
    private final FixtureRepository fixtureRepository;
    private final LiveFixtureBroadcastService broadcaster;
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread thread;

    @EventListener(ApplicationReadyEvent.class)
    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            stream.ensureConsumerGroup();
            republishRecoverable();
            thread = new Thread(this::consume, "redis-sync-worker");
            thread.start();
        } catch (RuntimeException exception) {
            running.set(false);
            throw exception;
        }
    }

    @Scheduled(fixedDelayString = "${api-football.sync.redis-queue.recovery-delay-ms:60000}")
    public void republishRecoverable() {
        for (AdminSyncJob job : adminJobs.recoverableQueueJobs()) {
            if (job.getQueuePayload() != null) {
                publisher.publishAdmin(job.getId(), job.getTask(),
                        ApiFootballSyncExecutionGuard.key(job.getTask(), job.getDetails()), job.getQueuePayload());
            }
        }
    }

    @Override
    public boolean isAutoStartup() {
        return false;
    }

    @Override
    public int getPhase() {
        // Stop the worker before the phase-0 Redis connection factory closes.
        return 1000;
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void stop(Runnable callback) {
        accepting.set(false);
        Thread waiter = new Thread(() -> {
            try {
                awaitWorker();
            } finally {
                running.set(false);
                callback.run();
            }
        }, "redis-sync-worker-shutdown");
        waiter.setDaemon(true);
        waiter.start();
    }

    @PreDestroy
    @Override
    public void stop() {
        accepting.set(false);
        awaitWorker();
        running.set(false);
    }

    private void awaitWorker() {
        Thread worker = thread;
        if (worker != null) {
            try {
                worker.join(SHUTDOWN_WAIT.toMillis());
                if (worker.isAlive()) {
                    log.warn("Redis sync worker did not finish within {}. Pending work will be retried.", SHUTDOWN_WAIT);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void consume() {
        String consumer = UUID.randomUUID().toString();
        while (accepting.get()) {
            try {
                ApiFootballSyncExecutionGuard.Lease leader;
                try {
                    leader = guard.acquire(ACTIVE_WORKER_KEY);
                } catch (ApiFootballSyncAlreadyRunningException exception) {
                    pauseBeforeRetry();
                    continue;
                }
                try {
                    log.info("Redis sync worker became active. consumer={}", consumer);
                    guard.withLease(leader, () -> {
                        consumeAsLeader(consumer);
                        return null;
                    });
                } finally {
                    guard.release(leader);
                    log.info("Redis sync worker stopped being active. consumer={}", consumer);
                }
            } catch (Exception exception) {
                if (accepting.get()) {
                    log.error("Redis sync worker could not read or process a job", exception);
                    pauseBeforeRetry();
                }
            }
        }
    }

    private void consumeAsLeader(String consumer) {
        while (accepting.get()) {
            ApiFootballSyncExecutionGuard.checkCurrentLease();
            List<RedisSyncJobStream.QueuedSyncJob> jobs = stream.readNew(consumer, Duration.ofMillis(200));
            if (jobs.isEmpty()) {
                jobs = stream.claimIdle(consumer, CLAIM_IDLE, 1);
            }
            for (var job : jobs) {
                if (accepting.get()) {
                    ApiFootballSyncExecutionGuard.checkCurrentLease();
                    process(job);
                }
            }
        }
    }

    private void pauseBeforeRetry() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            accepting.set(false);
        }
    }

    void process(RedisSyncJobStream.QueuedSyncJob job) {
        SyncQueueMessage message = job.message();
        if (stream.completed(message.jobId())) {
            if (message.jobId().startsWith("admin-") && adminJobActive(message.jobId())) {
                // A previous worker may have acknowledged a job whose DB status never became terminal.
                stream.clearCompleted(message.jobId());
            } else {
                acknowledge(job);
                return;
            }
        }
        try {
            boolean acquired = guard.executeIfAvailable(message.syncKey(), () -> {
                try {
                    SyncJobPayload payload = objectMapper.readValue(message.payload(), SyncJobPayload.class);
                    if (message.jobId().startsWith("admin-")) {
                        runAdmin(message, payload);
                    } else {
                        runScheduled(message.task(), payload);
                    }
                } catch (SyncLockLostException exception) {
                    throw exception;
                } catch (Exception exception) {
                    throw new IllegalStateException("Sync job execution failed", exception);
                }
            });
            if (acquired) {
                ApiFootballSyncExecutionGuard.checkCurrentLease();
                stream.markCompleted(message.jobId());
                acknowledge(job);
            }
        } catch (SyncLockLostException exception) {
            // Leave the delivery pending so another worker can reclaim it after its idle timeout.
            throw exception;
        } catch (Exception exception) {
            if (!message.jobId().startsWith("admin-")) {
                recordSyncFailure(message.task(), message.payload(), exception);
            }
            long failures = stream.recordFailure(job.id());
            log.error("Redis sync job failed. jobId={}, attempt={}", message.jobId(), failures, exception);
            if (failures >= MAX_FAILURES) {
                if (message.jobId().startsWith("admin-")) {
                    adminJobs.markFailed(Long.parseLong(message.jobId().substring(6)), "Sync failed after queue retries.");
                }
                stream.deadLetter(job);
            }
        }
    }

    private void acknowledge(RedisSyncJobStream.QueuedSyncJob job) {
        if (job.message().jobId().startsWith("admin-")) {
            stream.acknowledgeAdmin(job);
        } else {
            stream.acknowledgeScheduled(job);
        }
    }

    private void runAdmin(SyncQueueMessage message, SyncJobPayload payload) {
        Long id = Long.parseLong(message.jobId().substring(6));
        AdminSyncJob job = adminJobs.queueJob(id);
        if (job.getStatus() != AdminSyncJobStatus.QUEUED && job.getStatus() != AdminSyncJobStatus.RUNNING) {
            return;
        }
        adminRunner.runFromQueue(id, job.getAdminUser().getId(), job.getTask(), job.getTargetType(),
                job.getTargetId(), job.getDetails(), progress -> {
                    try {
                        return switch (job.getTask()) {
                            case "fixture-details" -> details.syncSeasonFixtureDetails(payload.season(), false, progress);
                            case "players" -> players.syncRegisteredPlayers(payload.league(), payload.season(), progress);
                            case "injuries" -> injuries.syncInjuries(payload.league(), payload.season(), progress);
                            default -> throw new IllegalArgumentException("Unknown admin sync task: " + job.getTask());
                        };
                    } catch (Exception exception) {
                        recordSyncFailure(job.getTask(), message.payload(), exception);
                        throw exception;
                    }
                });
        if (adminJobActive(message.jobId())) {
            throw new IllegalStateException("Admin sync job is still active after execution: " + id);
        }
    }

    private boolean adminJobActive(String jobId) {
        Long id = Long.parseLong(jobId.substring(6));
        AdminSyncJobStatus status = adminJobs.queueJob(id).getStatus();
        return status == AdminSyncJobStatus.QUEUED || status == AdminSyncJobStatus.RUNNING
                || status == AdminSyncJobStatus.CANCEL_REQUESTED;
    }

    private void recordSyncFailure(String task, String serializedPayload, Exception exception) {
        try {
            SyncJobPayload payload = objectMapper.readValue(serializedPayload, SyncJobPayload.class);
            switch (task) {
                case "seasons" -> syncStatus.recordFailureByKey(
                        "league-seasons:" + payload.league(), "League Seasons", exception);
                case "teams" -> syncStatus.recordFailure("teams", "Teams", payload.season(), exception);
                case "standings" -> syncStatus.recordFailure("standings", "Standings", payload.season(), exception);
                case "fixtures", "fixtures-live" -> syncStatus.recordFailure("fixtures", "Fixtures", payload.season(), exception);
                case "fixture-details", "fixture-details-live" -> syncStatus.recordFailure(
                        "fixture-details", "Season Details", payload.season(), exception);
                case "players" -> syncStatus.recordFailure("players", "Players", payload.season(), exception);
                case "injuries" -> syncStatus.recordFailure("injuries", "Injuries", payload.season(), exception);
                default -> log.warn("Unknown sync task failed. task={}", task);
            }
        } catch (Exception statusException) {
            log.error("Could not record sync failure. task={}", task, statusException);
        }
    }

    private void runScheduled(String task, SyncJobPayload payload) {
        switch (task) {
            case "seasons" -> seasons.syncLeagueSeasons(payload.league());
            case "teams" -> teams.syncTeams(payload.league(), payload.season());
            case "standings" -> standings.syncStandings(payload.league(), payload.season());
            case "fixtures" -> fixtures.syncSeasonFixtures(payload.league(), payload.season());
            case "fixtures-live" -> fixtures.syncLiveFixtures(payload.league(), payload.season());
            case "fixture-details" -> details.syncSeasonFixtureDetails(payload.season(), false);
            case "fixture-details-live" -> {
                List<Fixture> live = fixtureRepository.findAllByFixtureStatus("LIVE");
                if (!live.isEmpty()) {
                    details.syncFixtureDetailsWithResults(live, true).forEach(result ->
                            broadcaster.broadcastFixture(result.fixtureId(), result.latestEvent()));
                }
            }
            case "players" -> players.syncRegisteredPlayers(payload.league(), payload.season());
            case "injuries" -> injuries.syncInjuries(payload.league(), payload.season());
            default -> throw new IllegalArgumentException("Unknown scheduled sync task: " + task);
        }
    }
}
