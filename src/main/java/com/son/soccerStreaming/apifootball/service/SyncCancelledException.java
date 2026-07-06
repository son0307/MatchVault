package com.son.soccerStreaming.apifootball.service;

public class SyncCancelledException extends RuntimeException {
    public SyncCancelledException() {
        super("Sync cancellation requested.");
    }
}
