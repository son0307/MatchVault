package com.son.soccerStreaming.apifootball.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ApiFootballSyncExecutionGuard {

    private final Map<String, UUID> activeSyncKeys = new ConcurrentHashMap<>();

    public Lease acquire(String syncKey) {
        UUID ownerId = UUID.randomUUID();
        if (activeSyncKeys.putIfAbsent(syncKey, ownerId) != null) {
            throw new ApiFootballSyncAlreadyRunningException(syncKey);
        }
        return new Lease(syncKey, ownerId);
    }

    public void release(Lease lease) {
        if (lease != null) {
            activeSyncKeys.remove(lease.syncKey(), lease.ownerId());
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
            action.run();
            return true;
        } finally {
            release(lease);
        }
    }

    public static String key(String task, String details) {
        return task + ":" + details;
    }

    public record Lease(String syncKey, UUID ownerId) {
    }
}
