package com.son.soccerStreaming.apifootball.service;

public class SyncLockLostException extends RuntimeException {
    public SyncLockLostException(String syncKey) {
        super("Sync lock ownership was lost: " + syncKey);
    }
}
