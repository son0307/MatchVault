package com.son.soccerStreaming.apifootball.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiFootballSyncExecutionGuardTest {

    private final ApiFootballSyncExecutionGuard guard = new ApiFootballSyncExecutionGuard();

    @Test
    void rejectsTheSameKeyUntilItIsReleased() {
        ApiFootballSyncExecutionGuard.Lease lease = guard.acquire("players:league=39; season=2025");

        assertThatThrownBy(() -> guard.acquire("players:league=39; season=2025"))
                .isInstanceOf(ApiFootballSyncAlreadyRunningException.class);

        guard.release(lease);
        guard.acquire("players:league=39; season=2025");
    }

    @Test
    void anOldLeaseCannotReleaseANewerReservation() {
        ApiFootballSyncExecutionGuard.Lease oldLease = guard.acquire("fixtures:2025");
        guard.release(oldLease);
        guard.acquire("fixtures:2025");

        guard.release(oldLease);

        assertThatThrownBy(() -> guard.acquire("fixtures:2025"))
                .isInstanceOf(ApiFootballSyncAlreadyRunningException.class);
    }

    @Test
    void releasesTheKeyAfterScheduledWorkFails() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> guard.executeIfAvailable("injuries:2025", () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(guard.executeIfAvailable("injuries:2025", calls::incrementAndGet)).isTrue();
        assertThat(calls).hasValue(2);
    }
}
