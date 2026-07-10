package com.son.soccerStreaming.apifootball.entity;

public enum ApiFootballSyncState {
    NEVER_SYNCED,
    OK,
    STALE,
    FAILED,
    RETRY_PENDING
}
