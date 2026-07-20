package com.son.soccerStreaming.apifootball.scheduler;

import com.son.soccerStreaming.apifootball.service.ApiFootballFixtureDetailSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballSyncExecutionGuard;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import com.son.soccerStreaming.live.service.LiveFixtureBroadcastService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApiFootballFixtureDetailSyncSchedulerTest {

    @Test
    void skipsDetailSyncWhenThereAreNoLiveFixtures() {
        ApiFootballFixtureDetailSyncService detailSyncService = mock(ApiFootballFixtureDetailSyncService.class);
        LiveFixtureBroadcastService broadcastService = mock(LiveFixtureBroadcastService.class);
        FixtureRepository fixtureRepository = mock(FixtureRepository.class);
        ApiFootballSyncFailureRetryScheduler retryScheduler = mock(ApiFootballSyncFailureRetryScheduler.class);
        ApiFootballFixtureDetailSyncScheduler scheduler = new ApiFootballFixtureDetailSyncScheduler(
                detailSyncService,
                broadcastService,
                fixtureRepository,
                retryScheduler,
                new ApiFootballSyncExecutionGuard()
        );
        when(fixtureRepository.findAllByFixtureStatus("LIVE")).thenReturn(List.of());

        scheduler.syncLiveFixtureDetails();

        verifyNoInteractions(detailSyncService, broadcastService, retryScheduler);
    }

    @Test
    void skipsDetailSyncWhenTheSameJobIsAlreadyReserved() {
        ApiFootballFixtureDetailSyncService detailSyncService = mock(ApiFootballFixtureDetailSyncService.class);
        LiveFixtureBroadcastService broadcastService = mock(LiveFixtureBroadcastService.class);
        FixtureRepository fixtureRepository = mock(FixtureRepository.class);
        ApiFootballSyncFailureRetryScheduler retryScheduler = mock(ApiFootballSyncFailureRetryScheduler.class);
        ApiFootballSyncExecutionGuard guard = new ApiFootballSyncExecutionGuard();
        guard.acquire(ApiFootballSyncExecutionGuard.key("fixture-details-live", "live"));
        ApiFootballFixtureDetailSyncScheduler scheduler = new ApiFootballFixtureDetailSyncScheduler(
                detailSyncService,
                broadcastService,
                fixtureRepository,
                retryScheduler,
                guard
        );

        scheduler.syncLiveFixtureDetails();

        verifyNoInteractions(detailSyncService, broadcastService, fixtureRepository, retryScheduler);
    }
}
