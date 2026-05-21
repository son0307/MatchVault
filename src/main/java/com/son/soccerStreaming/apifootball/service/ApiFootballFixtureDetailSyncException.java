package com.son.soccerStreaming.apifootball.service;

import java.util.List;

public class ApiFootballFixtureDetailSyncException extends RuntimeException {

    private final List<List<Long>> failedChunks;

    public ApiFootballFixtureDetailSyncException(List<List<Long>> failedChunks, int totalChunks) {
        super("API-Football fixture detail sync failed. failedChunks="
                + failedChunks.size() + ", totalChunks=" + totalChunks);
        this.failedChunks = failedChunks.stream()
                .map(List::copyOf)
                .toList();
    }

    public List<List<Long>> getFailedChunks() {
        return failedChunks;
    }
}
