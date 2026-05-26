package com.son.soccerStreaming.live.service;

import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.fixture.repository.FixtureRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveFixturePollingSchedulerTest {

    private LiveFixtureSyncService liveFixtureSyncService;
    private SseService sseService;
    private FixtureRecordRepository fixtureRecordRepository;
    private MutableClock clock;
    private LiveFixturePollingScheduler scheduler;

    @BeforeEach
    void setUp() {
        liveFixtureSyncService = mock(LiveFixtureSyncService.class);
        sseService = mock(SseService.class);
        fixtureRecordRepository = mock(FixtureRecordRepository.class);
        clock = new MutableClock(Instant.parse("2026-05-26T00:00:00Z"));
        scheduler = new LiveFixturePollingScheduler(
                liveFixtureSyncService,
                sseService,
                fixtureRecordRepository,
                clock,
                3,
                60_000
        );

        when(sseService.getSubscribedFixtureIds()).thenReturn(List.of(100L));
        when(fixtureRecordRepository.findByFixtureId(100L)).thenReturn(Optional.of(fixtureWithStatus("LIVE")));
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
        scheduler = new LiveFixturePollingScheduler(
                liveFixtureSyncService,
                sseService,
                fixtureRecordRepository,
                clock,
                2,
                60_000
        );
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

        realSseService.subscribe(200L);

        List<Long> fixtureIds = realSseService.getSubscribedFixtureIds();

        assertThat(fixtureIds).containsExactly(200L);
    }

    @Test
    void skipsPollingTickWhenSubscribedFixtureLookupFails() {
        when(sseService.getSubscribedFixtureIds()).thenThrow(new IllegalStateException("bad subscriptions"));

        assertThatCode(() -> scheduler.pollLiveFixtures()).doesNotThrowAnyException();

        verify(liveFixtureSyncService, never()).syncFixture(100L);
    }

    @Test
    void skipsFixtureWhenStoredFixtureDoesNotExist() {
        when(fixtureRecordRepository.findByFixtureId(100L)).thenReturn(Optional.empty());

        scheduler.pollLiveFixtures();

        verify(liveFixtureSyncService, never()).syncFixture(100L);
    }

    @Test
    void skipsFixtureWhenStoredFixtureIsNotLive() {
        when(fixtureRecordRepository.findByFixtureId(100L)).thenReturn(Optional.of(fixtureWithStatus("SCHEDULED")));

        scheduler.pollLiveFixtures();

        verify(liveFixtureSyncService, never()).syncFixture(100L);
    }

    @Test
    void pollsFixtureWhenStoredFixtureIsLive() {
        scheduler.pollLiveFixtures();

        verify(liveFixtureSyncService).syncFixture(100L);
    }

    private Fixture fixtureWithStatus(String fixtureStatus) {
        return Fixture.builder()
                .fixtureId(100L)
                .fixtureStatus(fixtureStatus)
                .build();
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
