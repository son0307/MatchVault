package com.son.soccerStreaming.live.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveFixturePollingSchedulerTest {

    private LiveFixtureSyncService liveFixtureSyncService;
    private SseService sseService;
    private MutableClock clock;
    private LiveFixturePollingScheduler scheduler;

    @BeforeEach
    void setUp() {
        liveFixtureSyncService = mock(LiveFixtureSyncService.class);
        sseService = mock(SseService.class);
        clock = new MutableClock(Instant.parse("2026-05-26T00:00:00Z"));
        scheduler = new LiveFixturePollingScheduler(liveFixtureSyncService, sseService, clock, 3, 60_000);

        when(sseService.getSubscribedFixtureIds()).thenReturn(List.of(100L));
    }

    @Test
    void skipsFixtureDuringCooldownAfterConsecutiveFailures() {
        doThrow(new IllegalStateException("api down"))
                .when(liveFixtureSyncService)
                .syncFixture(100L);

        scheduler.pollLiveFixtures();
        scheduler.pollLiveFixtures();
        scheduler.pollLiveFixtures();
        scheduler.pollLiveFixtures();

        verify(liveFixtureSyncService, times(3)).syncFixture(100L);
    }

    @Test
    void retriesFixtureAfterCooldownExpires() {
        doThrow(new IllegalStateException("api down"))
                .when(liveFixtureSyncService)
                .syncFixture(100L);

        scheduler.pollLiveFixtures();
        scheduler.pollLiveFixtures();
        scheduler.pollLiveFixtures();
        clock.advanceMillis(60_000);
        scheduler.pollLiveFixtures();

        verify(liveFixtureSyncService, times(4)).syncFixture(100L);
    }

    @Test
    void successfulSyncClearsFailureState() {
        scheduler = new LiveFixturePollingScheduler(liveFixtureSyncService, sseService, clock, 2, 60_000);
        doThrow(new IllegalStateException("first failure"))
                .doNothing()
                .doThrow(new IllegalStateException("second failure"))
                .doNothing()
                .when(liveFixtureSyncService)
                .syncFixture(100L);

        scheduler.pollLiveFixtures();
        scheduler.pollLiveFixtures();
        scheduler.pollLiveFixtures();
        scheduler.pollLiveFixtures();

        verify(liveFixtureSyncService, times(4)).syncFixture(100L);
    }

    @Test
    void getSubscribedFixtureIdsReturnsCurrentNumericSubscriptions() {
        SseService realSseService = new SseService();

        realSseService.subscribe("200");
        realSseService.subscribe("not-number");

        List<Long> fixtureIds = realSseService.getSubscribedFixtureIds();

        org.assertj.core.api.Assertions.assertThat(fixtureIds).containsExactly(200L);
    }

    private static class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advanceMillis(long millis) {
            instant = instant.plusMillis(millis);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
