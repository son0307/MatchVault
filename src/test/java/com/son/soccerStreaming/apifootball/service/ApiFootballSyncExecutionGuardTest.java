package com.son.soccerStreaming.apifootball.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiFootballSyncExecutionGuardTest {

    private final ApiFootballSyncExecutionGuard guard = new ApiFootballSyncExecutionGuard(new InMemorySyncLockStore());

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

    @Test
    void aSharedStoreRejectsTheSameJobAcrossInstances() {
        InMemorySyncLockStore sharedStore = new InMemorySyncLockStore();
        ApiFootballSyncExecutionGuard blue = new ApiFootballSyncExecutionGuard(sharedStore);
        ApiFootballSyncExecutionGuard green = new ApiFootballSyncExecutionGuard(sharedStore);

        ApiFootballSyncExecutionGuard.Lease lease = blue.acquire("fixtures:league=39; season=2026");
        assertThatThrownBy(() -> green.acquire("fixtures:league=39; season=2026"))
                .isInstanceOf(ApiFootballSyncAlreadyRunningException.class);

        blue.release(lease);
        assertThat(green.acquire("fixtures:league=39; season=2026")).isNotNull();
    }

    @Test
    void aLostLeaseCannotBeReacquiredLocallyBeforeTheOldWorkerFinishes() {
        SyncLockStore store = new SyncLockStore() {
            @Override
            public boolean acquire(String key, String owner, Duration ttl) {
                return true;
            }

            @Override
            public boolean renew(String key, String owner, Duration ttl) {
                return false;
            }

            @Override
            public void release(String key, String owner) {
            }
        };
        ApiFootballSyncExecutionGuard instance = new ApiFootballSyncExecutionGuard(store);
        ApiFootballSyncExecutionGuard.Lease lease = instance.acquire("fixtures:39");

        instance.renewActiveLeases();

        assertThat(lease.valid()).isFalse();
        assertThatThrownBy(() -> instance.acquire("fixtures:39"))
                .isInstanceOf(ApiFootballSyncAlreadyRunningException.class);
        instance.release(lease);
        assertThat(instance.acquire("fixtures:39")).isNotNull();
    }

    @Test
    void stopsAtTheNextCheckAfterRenewalLosesTheLock() {
        SyncLockStore store = new SyncLockStore() {
            @Override
            public boolean acquire(String key, String owner, Duration ttl) {
                return true;
            }

            @Override
            public boolean renew(String key, String owner, Duration ttl) {
                return false;
            }

            @Override
            public void release(String key, String owner) {
            }
        };
        ApiFootballSyncExecutionGuard instance = new ApiFootballSyncExecutionGuard(store);
        AtomicInteger processedUnits = new AtomicInteger();

        assertThatThrownBy(() -> instance.executeIfAvailable("players:39", () -> {
            SyncProgressReporter.NO_OP.checkCancelled();
            instance.renewActiveLeases();
            SyncProgressReporter.NO_OP.checkCancelled();
            processedUnits.incrementAndGet();
        })).isInstanceOf(SyncLockLostException.class);

        assertThat(processedUnits).hasValue(0);
        ApiFootballSyncExecutionGuard.checkCurrentLease();
        assertThat(instance.acquire("players:39")).isNotNull();
    }

    @Test
    void nestedJobChecksTheOuterWorkerLease() {
        SyncLockStore store = new SyncLockStore() {
            @Override
            public boolean acquire(String key, String owner, Duration ttl) {
                return true;
            }

            @Override
            public boolean renew(String key, String owner, Duration ttl) {
                return !key.equals(RedisSyncJobWorker.ACTIVE_WORKER_KEY);
            }

            @Override
            public void release(String key, String owner) {
            }
        };
        ApiFootballSyncExecutionGuard instance = new ApiFootballSyncExecutionGuard(store);
        ApiFootballSyncExecutionGuard.Lease leader = instance.acquire(RedisSyncJobWorker.ACTIVE_WORKER_KEY);

        try {
            assertThatThrownBy(() -> instance.withLease(leader, () -> {
                instance.executeIfAvailable("players:39", () -> {
                    instance.renewActiveLeases();
                    ApiFootballSyncExecutionGuard.checkCurrentLease();
                });
                return null;
            })).isInstanceOf(SyncLockLostException.class);
        } finally {
            instance.release(leader);
        }
    }
}
