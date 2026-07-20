package com.son.soccerStreaming.apifootball.service;

public class ApiFootballSyncAlreadyRunningException extends RuntimeException {

    public ApiFootballSyncAlreadyRunningException(String syncKey) {
        super("API-Football synchronization is already active. syncKey=" + syncKey);
    }
}
