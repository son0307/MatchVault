package com.son.soccerStreaming.apifootball.service;

import java.time.Duration;

interface SyncLockStore {
    boolean acquire(String syncKey, String owner, Duration ttl);

    boolean renew(String syncKey, String owner, Duration ttl);

    void release(String syncKey, String owner);
}
