package com.son.soccerStreaming.apifootball.service;

/** A durable command, not an in-memory Runnable or a serialized Java object. */
public record SyncQueueMessage(String jobId, String task, String syncKey, String payload) {
    public SyncQueueMessage {
        if (blank(jobId) || blank(task) || blank(syncKey) || payload == null) {
            throw new IllegalArgumentException("A sync queue message requires an id, task, key, and payload");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
