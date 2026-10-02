package com.son.soccerStreaming.apifootball.service;

import tools.jackson.databind.ObjectMapper;
import com.son.soccerStreaming.admin.service.AdminSyncJobService;
import com.son.soccerStreaming.admin.service.AdminSyncTaskRunner;
import com.son.soccerStreaming.admin.entity.AdminSyncJob;
import com.son.soccerStreaming.admin.entity.AdminSyncJobStatus;
import com.son.soccerStreaming.auth.entity.AppUser;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import com.son.soccerStreaming.live.service.LiveFixtureBroadcastService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.support.GenericApplicationContext;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisSyncJobWorkerTest {
    @Test
    void readsExistingQueuedPayloadWithRetiredDelayField() throws Exception {
        SyncJobPayload payload = new ObjectMapper().readValue(
                "{\"league\":39,\"season\":2025,\"delayMs\":7000}", SyncJobPayload.class);

        org.assertj.core.api.Assertions.assertThat(payload).isEqualTo(new SyncJobPayload(39, 2025));
    }

    @Test
    void executesScheduledMessageAndAcknowledgesOnlyAfterCompletion() {
        RedisSyncJobStream stream = mock(RedisSyncJobStream.class);
        ApiFootballSyncExecutionGuard guard = mock(ApiFootballSyncExecutionGuard.class);
        ApiFootballTeamSyncService teams = mock(ApiFootballTeamSyncService.class);
        when(guard.executeIfAvailable(eq("teams:39"), any())).thenAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return true;
        });
        RedisSyncJobWorker worker = new RedisSyncJobWorker(stream, mock(SyncJobPublisher.class),
                new ObjectMapper(), guard, mock(AdminSyncJobService.class), mock(AdminSyncTaskRunner.class),
                mock(ApiFootballSyncStatusService.class),
                mock(LeagueSeasonCoverageSyncService.class), teams, mock(ApiFootballStandingSyncService.class),
                mock(ApiFootballFixtureSyncService.class), mock(ApiFootballFixtureDetailSyncService.class),
                mock(ApiFootballPlayerSyncService.class), mock(ApiFootballInjurySyncService.class),
                mock(FixtureRepository.class), mock(LiveFixtureBroadcastService.class));
        var message = new SyncQueueMessage("teams:2025-01-01", "teams", "teams:39",
                "{\"league\":39,\"season\":2025}");
        var queued = new RedisSyncJobStream.QueuedSyncJob(RecordId.of("1-0"), message);

        worker.process(queued);

        verify(teams).syncTeams(39, 2025);
        verify(stream).markCompleted(message.jobId());
        verify(stream).acknowledgeScheduled(queued);
    }

    @Test
    void completedMarkerDoesNotDiscardAnAdminJobStillRunningInTheDatabase() {
        RedisSyncJobStream stream = mock(RedisSyncJobStream.class);
        ApiFootballSyncExecutionGuard guard = mock(ApiFootballSyncExecutionGuard.class);
        AdminSyncJobService adminJobs = mock(AdminSyncJobService.class);
        AdminSyncJob job = mock(AdminSyncJob.class);
        AppUser admin = mock(AppUser.class);
        when(stream.completed("admin-25")).thenReturn(true);
        when(adminJobs.queueJob(25L)).thenReturn(job);
        when(job.getStatus()).thenReturn(AdminSyncJobStatus.RUNNING);
        when(job.getAdminUser()).thenReturn(admin);
        when(admin.getId()).thenReturn(1L);
        when(guard.executeIfAvailable(eq("fixture-details:season=2025"), any())).thenAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return true;
        });
        var message = new SyncQueueMessage("admin-25", "fixture-details", "fixture-details:season=2025",
                "{\"season\":2025}");
        var queued = new RedisSyncJobStream.QueuedSyncJob(RecordId.of("25-0"), message);
        RedisSyncJobWorker worker = new RedisSyncJobWorker(stream, mock(SyncJobPublisher.class),
                new ObjectMapper(), guard, adminJobs, mock(AdminSyncTaskRunner.class),
                mock(ApiFootballSyncStatusService.class), mock(LeagueSeasonCoverageSyncService.class),
                mock(ApiFootballTeamSyncService.class), mock(ApiFootballStandingSyncService.class),
                mock(ApiFootballFixtureSyncService.class), mock(ApiFootballFixtureDetailSyncService.class),
                mock(ApiFootballPlayerSyncService.class), mock(ApiFootballInjurySyncService.class),
                mock(FixtureRepository.class), mock(LiveFixtureBroadcastService.class));

        worker.process(queued);

        verify(stream).clearCompleted("admin-25");
        verify(stream).recordFailure(queued.id());
        verify(stream, never()).markCompleted("admin-25");
        verify(stream, never()).acknowledgeAdmin(queued);
    }

    @Test
    void lostWorkerLeaseLeavesTheMessagePendingWithoutCountingAJobFailure() {
        RedisSyncJobStream stream = mock(RedisSyncJobStream.class);
        ApiFootballSyncExecutionGuard guard = mock(ApiFootballSyncExecutionGuard.class);
        var message = new SyncQueueMessage("teams:2025-01-01", "teams", "teams:39",
                "{\"league\":39,\"season\":2025}");
        var queued = new RedisSyncJobStream.QueuedSyncJob(RecordId.of("1-0"), message);
        when(guard.executeIfAvailable(eq("teams:39"), any()))
                .thenThrow(new SyncLockLostException(RedisSyncJobWorker.ACTIVE_WORKER_KEY));
        RedisSyncJobWorker worker = new RedisSyncJobWorker(stream, mock(SyncJobPublisher.class),
                new ObjectMapper(), guard, mock(AdminSyncJobService.class), mock(AdminSyncTaskRunner.class),
                mock(ApiFootballSyncStatusService.class), mock(LeagueSeasonCoverageSyncService.class),
                mock(ApiFootballTeamSyncService.class), mock(ApiFootballStandingSyncService.class),
                mock(ApiFootballFixtureSyncService.class), mock(ApiFootballFixtureDetailSyncService.class),
                mock(ApiFootballPlayerSyncService.class), mock(ApiFootballInjurySyncService.class),
                mock(FixtureRepository.class), mock(LiveFixtureBroadcastService.class));

        assertThatThrownBy(() -> worker.process(queued)).isInstanceOf(SyncLockLostException.class);

        verify(stream, never()).recordFailure(queued.id());
        verify(stream, never()).markCompleted(message.jobId());
        verify(stream, never()).acknowledgeScheduled(queued);
    }

    @Test
    void shutdownWaitsForTheCurrentJobBeforeCompletingItsLifecyclePhase() throws Exception {
        RedisSyncJobStream stream = mock(RedisSyncJobStream.class);
        ApiFootballSyncExecutionGuard guard = spy(new ApiFootballSyncExecutionGuard());
        AdminSyncJobService adminJobs = mock(AdminSyncJobService.class);
        CountDownLatch processing = new CountDownLatch(1);
        CountDownLatch finishJob = new CountDownLatch(1);
        CountDownLatch redisStopped = new CountDownLatch(1);
        var message = new SyncQueueMessage("teams:2025-01-01", "teams", "teams:39",
                "{\"league\":39,\"season\":2025}");
        var queued = new RedisSyncJobStream.QueuedSyncJob(RecordId.of("1-0"), message);
        when(adminJobs.recoverableQueueJobs()).thenReturn(List.of());
        when(stream.readNew(any(), eq(Duration.ofMillis(200)))).thenReturn(List.of(queued));
        doAnswer(invocation -> {
            processing.countDown();
            if (!finishJob.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("The test job did not finish");
            }
            return true;
        }).when(guard).executeIfAvailable(eq("teams:39"), any());
        RedisSyncJobWorker worker = new RedisSyncJobWorker(stream, mock(SyncJobPublisher.class),
                new ObjectMapper(), guard, adminJobs, mock(AdminSyncTaskRunner.class),
                mock(ApiFootballSyncStatusService.class), mock(LeagueSeasonCoverageSyncService.class),
                mock(ApiFootballTeamSyncService.class), mock(ApiFootballStandingSyncService.class),
                mock(ApiFootballFixtureSyncService.class), mock(ApiFootballFixtureDetailSyncService.class),
                mock(ApiFootballPlayerSyncService.class), mock(ApiFootballInjurySyncService.class),
                mock(FixtureRepository.class), mock(LiveFixtureBroadcastService.class));
        AtomicBoolean redisRunning = new AtomicBoolean();
        SmartLifecycle redis = new SmartLifecycle() {
            @Override
            public void start() {
                redisRunning.set(true);
            }

            @Override
            public void stop() {
                redisRunning.set(false);
            }

            @Override
            public void stop(Runnable callback) {
                redisStopped.countDown();
                stop();
                callback.run();
            }

            @Override
            public boolean isRunning() {
                return redisRunning.get();
            }

            @Override
            public int getPhase() {
                return 0;
            }
        };
        GenericApplicationContext context = new GenericApplicationContext();
        context.getBeanFactory().registerSingleton("redis", redis);
        context.getBeanFactory().registerSingleton("worker", worker);
        context.refresh();
        Thread closer = null;

        try {
            worker.start();
            assertThat(worker.getPhase()).isPositive();
            assertThat(worker.isRunning()).isTrue();
            assertThat(processing.await(2, TimeUnit.SECONDS)).isTrue();
            closer = new Thread(context::close, "test-context-shutdown");
            closer.start();
            assertThat(redisStopped.await(100, TimeUnit.MILLISECONDS)).isFalse();
        } finally {
            finishJob.countDown();
            if (closer == null) {
                context.close();
            }
        }

        assertThat(redisStopped.await(2, TimeUnit.SECONDS)).isTrue();
        closer.join(2000);
        assertThat(closer.isAlive()).isFalse();
        verify(stream).markCompleted(message.jobId());
        verify(stream).acknowledgeScheduled(queued);
    }

    @Test
    void inactiveWorkerDoesNotReadUntilTheActiveWorkerReleasesItsLease() throws Exception {
        InMemorySyncLockStore sharedStore = new InMemorySyncLockStore();
        ApiFootballSyncExecutionGuard blue = new ApiFootballSyncExecutionGuard(sharedStore);
        ApiFootballSyncExecutionGuard green = new ApiFootballSyncExecutionGuard(sharedStore);
        ApiFootballSyncExecutionGuard.Lease leader = blue.acquire(RedisSyncJobWorker.ACTIVE_WORKER_KEY);
        RedisSyncJobStream stream = mock(RedisSyncJobStream.class);
        CountDownLatch readStarted = new CountDownLatch(1);
        when(stream.readNew(any(), eq(Duration.ofMillis(200)))).thenAnswer(invocation -> {
            readStarted.countDown();
            return List.of();
        });
        RedisSyncJobWorker worker = new RedisSyncJobWorker(stream, mock(SyncJobPublisher.class),
                new ObjectMapper(), green, mock(AdminSyncJobService.class), mock(AdminSyncTaskRunner.class),
                mock(ApiFootballSyncStatusService.class), mock(LeagueSeasonCoverageSyncService.class),
                mock(ApiFootballTeamSyncService.class), mock(ApiFootballStandingSyncService.class),
                mock(ApiFootballFixtureSyncService.class), mock(ApiFootballFixtureDetailSyncService.class),
                mock(ApiFootballPlayerSyncService.class), mock(ApiFootballInjurySyncService.class),
                mock(FixtureRepository.class), mock(LiveFixtureBroadcastService.class));

        try {
            worker.start();
            assertThat(readStarted.await(200, TimeUnit.MILLISECONDS)).isFalse();
            blue.release(leader);
            assertThat(readStarted.await(2, TimeUnit.SECONDS)).isTrue();
        } finally {
            blue.release(leader);
            worker.stop();
        }
    }
}
