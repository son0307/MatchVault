package com.son.soccerStreaming.apifootball.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "scheduled_sync_job", indexes = @Index(name = "idx_scheduled_sync_job_status", columnList = "status"),
        uniqueConstraints = @UniqueConstraint(name = "uk_scheduled_sync_job_active_key", columnNames = "active_sync_key"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduledSyncJob {
    @Id
    @Column(length = 250)
    private String jobId;

    @Column(nullable = false, length = 40)
    private String task;

    @Column(nullable = false, length = 250)
    private String syncKey;

    // MySQL permits multiple NULLs in a unique index; only unfinished jobs occupy this key.
    @Column(name = "active_sync_key", length = 250)
    private String activeSyncKey;

    @Column(nullable = false, length = 500)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScheduledSyncJobStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    public static ScheduledSyncJob queued(String jobId, String task, String syncKey, String payload) {
        ScheduledSyncJob job = new ScheduledSyncJob();
        job.jobId = jobId;
        job.task = task;
        job.syncKey = syncKey;
        job.activeSyncKey = syncKey;
        job.payload = payload;
        job.status = ScheduledSyncJobStatus.QUEUED;
        job.createdAt = LocalDateTime.now();
        return job;
    }

    public void start() {
        if (status != ScheduledSyncJobStatus.QUEUED && status != ScheduledSyncJobStatus.RUNNING) {
            throw new IllegalStateException("Cannot start completed scheduled job: " + jobId);
        }
        status = ScheduledSyncJobStatus.RUNNING;
        startedAt = LocalDateTime.now();
    }

    public void succeed() {
        status = ScheduledSyncJobStatus.SUCCEEDED;
        activeSyncKey = null;
        completedAt = LocalDateTime.now();
    }

    public int fail(int maxAttempts) {
        if (status == ScheduledSyncJobStatus.SUCCEEDED || status == ScheduledSyncJobStatus.FAILED) {
            return attempts;
        }
        attempts++;
        if (attempts >= maxAttempts) {
            status = ScheduledSyncJobStatus.FAILED;
            activeSyncKey = null;
            completedAt = LocalDateTime.now();
        }
        return attempts;
    }
}
