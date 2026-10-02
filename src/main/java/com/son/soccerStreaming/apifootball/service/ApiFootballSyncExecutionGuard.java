package com.son.soccerStreaming.apifootball.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

@Slf4j
@Component
public class ApiFootballSyncExecutionGuard {
    private static final Duration LOCK_TTL = Duration.ofMinutes(2);
    private static final long RENEW_INTERVAL_SECONDS = 20;
    private static final ThreadLocal<LeaseContext> CURRENT_LEASE = new ThreadLocal<>();

    private final SyncLockStore lockStore;
    private final Map<String, Lease> localLeases = new ConcurrentHashMap<>();
    private final ScheduledExecutorService renewer = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "sync-lock-renewal");
        thread.setDaemon(true);
        return thread;
    });

    @Autowired
    public ApiFootballSyncExecutionGuard(SyncLockStore lockStore) {
        this.lockStore = lockStore;
    }

    // Existing plain unit tests construct the guard without a Spring context.
    public ApiFootballSyncExecutionGuard() {
        this(new InMemorySyncLockStore());
    }

    @PostConstruct
    void startRenewal() {
        renewer.scheduleWithFixedDelay(this::renewActiveLeases,
                RENEW_INTERVAL_SECONDS, RENEW_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    @PreDestroy
    void stopRenewal() {
        renewer.shutdownNow();
    }

    public Lease acquire(String syncKey) {
        if (localLeases.containsKey(syncKey)) {
            throw new ApiFootballSyncAlreadyRunningException(syncKey);
        }
        UUID ownerId = UUID.randomUUID();
        if (!lockStore.acquire(syncKey, ownerId.toString(), LOCK_TTL)) {
            throw new ApiFootballSyncAlreadyRunningException(syncKey);
        }
        Lease lease = new Lease(syncKey, ownerId);
        if (localLeases.putIfAbsent(syncKey, lease) != null) {
            lockStore.release(syncKey, ownerId.toString());
            throw new ApiFootballSyncAlreadyRunningException(syncKey);
        }
        return lease;
    }

    public void release(Lease lease) {
        if (lease != null) {
            localLeases.remove(lease.syncKey(), lease);
            lockStore.release(lease.syncKey(), lease.ownerId().toString());
        }
    }

    void renewActiveLeases() {
        for (Lease lease : localLeases.values()) {
            if (!lease.valid()) {
                continue;
            }
            try {
                if (!lockStore.renew(lease.syncKey(), lease.ownerId().toString(), LOCK_TTL)) {
                    lease.valid.set(false);
                    log.error("Sync lock ownership was lost. syncKey={}", lease.syncKey());
                }
            } catch (RuntimeException exception) {
                lease.valid.set(false);
                log.error("Could not renew sync lock. syncKey={}", lease.syncKey(), exception);
            }
        }
    }

    public boolean executeIfAvailable(String syncKey, Runnable action) {
        final Lease lease;
        try {
            lease = acquire(syncKey);
        } catch (ApiFootballSyncAlreadyRunningException exception) {
            return false;
        }
        try {
            withLease(lease, () -> {
                action.run();
                return null;
            });
            return true;
        } finally {
            release(lease);
        }
    }

    public <T> T withLease(Lease lease, Supplier<T> action) {
        LeaseContext previous = CURRENT_LEASE.get();
        CURRENT_LEASE.set(new LeaseContext(lease, previous));
        try {
            checkCurrentLease();
            T result = action.get();
            checkCurrentLease();
            return result;
        } finally {
            if (previous == null) {
                CURRENT_LEASE.remove();
            } else {
                CURRENT_LEASE.set(previous);
            }
        }
    }

    public static void checkCurrentLease() {
        for (LeaseContext context = CURRENT_LEASE.get(); context != null; context = context.previous()) {
            context.lease().checkValid();
        }
    }

    private record LeaseContext(Lease lease, LeaseContext previous) {
    }

    public static String key(String task, String details) {
        return task + ":" + details;
    }

    public static final class Lease {
        private final String syncKey;
        private final UUID ownerId;
        private final AtomicBoolean valid = new AtomicBoolean(true);

        private Lease(String syncKey, UUID ownerId) {
            this.syncKey = syncKey;
            this.ownerId = ownerId;
        }

        public String syncKey() {
            return syncKey;
        }

        public UUID ownerId() {
            return ownerId;
        }

        public boolean valid() {
            return valid.get();
        }

        public void checkValid() {
            if (!valid()) {
                throw new SyncLockLostException(syncKey);
            }
        }
    }
}
