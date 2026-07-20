package com.son.soccerStreaming.apifootball.scheduler;

import com.son.soccerStreaming.apifootball.service.ApiFootballSyncExecutionGuard;
import com.son.soccerStreaming.apifootball.service.ApiFootballSyncStatusService;
import com.son.soccerStreaming.global.externalapi.ExternalApiErrorCategory;
import com.son.soccerStreaming.global.externalapi.ExternalApiException;
import com.son.soccerStreaming.global.externalapi.ExternalApiProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiFootballSyncFailureRetrySchedulerTest {

    private final ApiFootballSyncStatusService syncStatusService = mock(ApiFootballSyncStatusService.class);
    private final ApiFootballSyncExecutionGuard executionGuard = new ApiFootballSyncExecutionGuard();
    private final ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
    private final ApiFootballSyncFailureRetryScheduler scheduler =
            new ApiFootballSyncFailureRetryScheduler(syncStatusService, executionGuard, executor);

    ApiFootballSyncFailureRetrySchedulerTest() {
        ReflectionTestUtils.setField(scheduler, "enabled", true);
        ReflectionTestUtils.setField(scheduler, "maxAttempts", 2);
        ReflectionTestUtils.setField(scheduler, "initialDelayMinutes", 1L);
        ReflectionTestUtils.setField(scheduler, "delayMultiplier", 5L);
        ReflectionTestUtils.setField(scheduler, "maxDelayMinutes", 30L);
        when(executor.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenAnswer(invocation -> mock(ScheduledFuture.class));
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdown();
    }

    @Test
    void increasesFallbackDelayAndCapsItAtConfiguredMaximum() {
        assertThat(scheduler.retryDelay(0, new RuntimeException())).isEqualTo(Duration.ofMinutes(1));
        assertThat(scheduler.retryDelay(1, new RuntimeException())).isEqualTo(Duration.ofMinutes(5));
        assertThat(scheduler.retryDelay(2, new RuntimeException())).isEqualTo(Duration.ofMinutes(25));
        assertThat(scheduler.retryDelay(3, new RuntimeException())).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void retryAfterOverridesFallbackDelayWithoutSlowRetryCap() {
        ExternalApiException failure = retryableFailure(Duration.ofHours(2));

        assertThat(scheduler.retryDelay(1, failure)).isEqualTo(Duration.ofHours(2));
        assertThat(scheduler.retryDelay(1, new RuntimeException(failure))).isEqualTo(Duration.ofHours(2));
    }

    @Test
    void retryFailureUsesTheLatestRetryAfterForTheNextSchedule() {
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Long> delayCaptor = ArgumentCaptor.forClass(Long.class);
        ExternalApiException latestFailure = retryableFailure(Duration.ofMinutes(7));

        scheduler.schedule("fixtures:daily:39:2025", "fixtures:league=39; season=2025",
                "fixture sync", new RuntimeException(), () -> {
                    throw latestFailure;
                });
        verify(executor).schedule(taskCaptor.capture(), delayCaptor.capture(), eq(TimeUnit.MILLISECONDS));
        taskCaptor.getValue().run();

        verify(executor, times(2)).schedule(any(Runnable.class), delayCaptor.capture(), eq(TimeUnit.MILLISECONDS));
        assertThat(delayCaptor.getAllValues()).contains(Duration.ofMinutes(7).toMillis());
    }

    @Test
    void doesNotRegisterTheSameRetryKeyTwice() {
        scheduler.schedule("teams:39:2025", "teams:league=39; season=2025",
                "team sync", new RuntimeException(), () -> { });
        scheduler.schedule("teams:39:2025", "teams:league=39; season=2025",
                "team sync", new RuntimeException(), () -> { });

        assertThat(scheduler.pendingRetryCount()).isEqualTo(1);
        verify(executor, times(1)).schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void cancellingAnExecutionScopePreventsCapturedStaleTasksFromRunning() {
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        Runnable firstAction = mock(Runnable.class);
        Runnable secondAction = mock(Runnable.class);
        Runnable unrelatedAction = mock(Runnable.class);

        scheduler.schedule("teams:39:2025", "teams:league=39; season=2025",
                "scheduled teams", new RuntimeException(), firstAction);
        scheduler.schedule("startup:teams:39:2025", "teams:league=39; season=2025",
                "startup teams", new RuntimeException(), secondAction);
        scheduler.schedule("teams:40:2025", "teams:league=40; season=2025",
                "other teams", new RuntimeException(), unrelatedAction);
        verify(executor, times(3)).schedule(taskCaptor.capture(), anyLong(), eq(TimeUnit.MILLISECONDS));

        assertThat(scheduler.cancelPendingByExecutionKey("teams:league=39; season=2025")).isEqualTo(2);
        taskCaptor.getAllValues().get(0).run();
        taskCaptor.getAllValues().get(1).run();

        assertThat(scheduler.pendingRetryCount()).isEqualTo(1);
        verify(firstAction, never()).run();
        verify(secondAction, never()).run();
        verify(unrelatedAction, never()).run();
        verify(executor, times(3)).schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void partialRetrySuccessKeepsSiblingsAndMarksSuccessAfterTheLastOne() {
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        String executionKey = "players:league=39; season=2025";

        scheduler.schedule("registered-players:39:2025:team:1", executionKey,
                "team 1", new RuntimeException(), () -> { });
        scheduler.schedule("registered-players:39:2025:team:2", executionKey,
                "team 2", new RuntimeException(), () -> { });
        verify(executor, times(2)).schedule(taskCaptor.capture(), anyLong(), eq(TimeUnit.MILLISECONDS));

        taskCaptor.getAllValues().get(0).run();
        assertThat(scheduler.pendingRetryCount()).isEqualTo(1);
        verify(syncStatusService, never()).recordSuccessByKey(any(), any());

        taskCaptor.getAllValues().get(1).run();
        assertThat(scheduler.pendingRetryCount()).isZero();
        verify(syncStatusService).recordSuccessByKey("players:2025", "Players 2025");
    }

    @Test
    void successfulSiblingDoesNotHideATerminalPartialRetryFailure() {
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        String executionKey = "players:league=39; season=2025";

        scheduler.schedule("registered-players:39:2025:team:1", executionKey,
                "team 1", new RuntimeException(), () -> {
                    throw nonRetryableFailure();
                });
        scheduler.schedule("registered-players:39:2025:team:2", executionKey,
                "team 2", new RuntimeException(), () -> { });
        verify(executor, times(2)).schedule(taskCaptor.capture(), anyLong(), eq(TimeUnit.MILLISECONDS));

        taskCaptor.getAllValues().forEach(Runnable::run);

        assertThat(scheduler.pendingRetryCount()).isZero();
        verify(syncStatusService, never()).recordSuccessByKey(any(), any());
        verify(syncStatusService).recordFailureByKey(eq("players:2025"), eq("Players 2025"), any());
    }

    @Test
    void activeSynchronizationDefersWithoutRunningTheRetryAction() {
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        String executionKey = "fixtures:league=39; season=2025";
        Runnable action = mock(Runnable.class);
        ApiFootballSyncExecutionGuard.Lease lease = executionGuard.acquire(executionKey);

        scheduler.schedule("fixtures:daily:39:2025", executionKey,
                "fixture sync", new RuntimeException(), action);
        verify(executor).schedule(taskCaptor.capture(), anyLong(), eq(TimeUnit.MILLISECONDS));
        taskCaptor.getValue().run();

        assertThat(scheduler.pendingRetryCount()).isEqualTo(1);
        verify(action, never()).run();
        verify(executor, times(2)).schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS));
        verify(syncStatusService, times(1)).recordRetryPendingByKey(any(), any(), any());
        executionGuard.release(lease);
    }

    private ExternalApiException retryableFailure(Duration retryAfter) {
        return new ExternalApiException(
                ExternalApiProvider.API_FOOTBALL,
                "fixtures",
                ExternalApiErrorCategory.RATE_LIMITED,
                429,
                true,
                retryAfter,
                "rate limited",
                null
        );
    }

    private ExternalApiException nonRetryableFailure() {
        return new ExternalApiException(
                ExternalApiProvider.API_FOOTBALL,
                "players",
                ExternalApiErrorCategory.BAD_REQUEST,
                400,
                false,
                null,
                "bad request",
                null
        );
    }
}
