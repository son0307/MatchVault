package com.son.soccerStreaming.apifootball.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "api-football.sync.distributed-lock.enabled", havingValue = "false", matchIfMissing = true)
class InMemorySyncLockStore implements SyncLockStore {
    private final Map<String, String> owners = new ConcurrentHashMap<>();

    @Override
    public boolean acquire(String syncKey, String owner, Duration ttl) {
        return owners.putIfAbsent(syncKey, owner) == null;
    }

    @Override
    public boolean renew(String syncKey, String owner, Duration ttl) {
        return owner.equals(owners.get(syncKey));
    }

    @Override
    public void release(String syncKey, String owner) {
        owners.remove(syncKey, owner);
    }
}
