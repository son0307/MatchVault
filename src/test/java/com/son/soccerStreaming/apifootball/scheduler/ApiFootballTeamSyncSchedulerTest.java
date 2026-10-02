package com.son.soccerStreaming.apifootball.scheduler;

import com.son.soccerStreaming.apifootball.service.ApiFootballSyncExecutionGuard;
import com.son.soccerStreaming.apifootball.service.ApiFootballTeamSyncService;
import com.son.soccerStreaming.apifootball.service.SyncJobPublisher;
import com.son.soccerStreaming.apifootball.service.SyncJobPayload;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApiFootballTeamSyncSchedulerTest {

    @Test
    void queueModePublishesTheScheduledJobWithoutRunningItLocally() {
        ApiFootballTeamSyncService syncService = mock(ApiFootballTeamSyncService.class);
        ApiFootballSyncFailureRetryScheduler retryScheduler = mock(ApiFootballSyncFailureRetryScheduler.class);
        SyncJobPublisher publisher = mock(SyncJobPublisher.class);
        when(publisher.enabled()).thenReturn(true);
        ApiFootballTeamSyncScheduler scheduler = new ApiFootballTeamSyncScheduler(
                syncService, retryScheduler, new ApiFootballSyncExecutionGuard(), publisher);
        ReflectionTestUtils.setField(scheduler, "league", 39);
        ReflectionTestUtils.setField(scheduler, "season", 2025);

        scheduler.syncTeams();

        verify(publisher).publishScheduled("teams", "teams:league=39; season=2025",
                new SyncJobPayload(39, 2025), ChronoUnit.DAYS);
        verifyNoInteractions(syncService, retryScheduler);
    }

    @Test
    void successfulAutomaticSyncCancelsPendingRetriesForTheSameScope() {
        ApiFootballTeamSyncService syncService = mock(ApiFootballTeamSyncService.class);
        ApiFootballSyncFailureRetryScheduler retryScheduler = mock(ApiFootballSyncFailureRetryScheduler.class);
        ApiFootballTeamSyncScheduler scheduler = scheduler(syncService, retryScheduler);

        scheduler.syncTeams();

        verify(retryScheduler).cancelPendingByExecutionKey("teams:league=39; season=2025");
        verify(retryScheduler, never()).schedule(any(), any(), any(), any(), any());
    }

    @Test
    void failedAutomaticSyncSchedulesSlowRetryWithTheOriginalFailure() {
        ApiFootballTeamSyncService syncService = mock(ApiFootballTeamSyncService.class);
        ApiFootballSyncFailureRetryScheduler retryScheduler = mock(ApiFootballSyncFailureRetryScheduler.class);
        ApiFootballTeamSyncScheduler scheduler = scheduler(syncService, retryScheduler);
        RuntimeException failure = new RuntimeException("upstream failed");
        when(syncService.syncTeams(39, 2025)).thenThrow(failure);
        when(retryScheduler.shouldRetry(failure)).thenReturn(true);

        scheduler.syncTeams();

        verify(retryScheduler).schedule(
                eq("teams:39:2025"),
                eq("teams:league=39; season=2025"),
                eq("team sync league=39 season=2025"),
                eq(failure),
                any(Runnable.class)
        );
        verify(retryScheduler, never()).cancelPendingByExecutionKey(any());
    }

    private ApiFootballTeamSyncScheduler scheduler(ApiFootballTeamSyncService syncService,
                                                   ApiFootballSyncFailureRetryScheduler retryScheduler) {
        ApiFootballTeamSyncScheduler scheduler = new ApiFootballTeamSyncScheduler(
                syncService, retryScheduler, new ApiFootballSyncExecutionGuard(), null);
        ReflectionTestUtils.setField(scheduler, "league", 39);
        ReflectionTestUtils.setField(scheduler, "season", 2025);
        return scheduler;
    }
}
