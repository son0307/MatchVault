package com.son.soccerStreaming.admin.entity;

public enum AdminSyncJobStatus {
    QUEUED,
    RUNNING,
    CANCEL_REQUESTED,
    CANCELLED,
    SUCCEEDED,
    PARTIAL_FAILED,
    FAILED;

    public boolean isActive() {
        return this == QUEUED || this == RUNNING || this == CANCEL_REQUESTED;
    }
}
