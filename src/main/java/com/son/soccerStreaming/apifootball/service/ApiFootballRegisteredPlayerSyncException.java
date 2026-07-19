package com.son.soccerStreaming.apifootball.service;

import java.util.List;

public class ApiFootballRegisteredPlayerSyncException extends RuntimeException {

    private final List<Long> failedTeamIds;

    public ApiFootballRegisteredPlayerSyncException(List<Long> failedTeamIds, Throwable cause) {
        super("API-Football registered players sync failed. failedTeams=" + failedTeamIds.size(), cause);
        this.failedTeamIds = List.copyOf(failedTeamIds);
    }

    public List<Long> getFailedTeamIds() {
        return failedTeamIds;
    }
}
