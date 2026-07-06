package com.son.soccerStreaming.admin.entity;

import com.son.soccerStreaming.auth.entity.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "admin_sync_job", indexes = {
        @Index(name = "idx_admin_sync_job_created_at", columnList = "created_at"),
        @Index(name = "idx_admin_sync_job_status", columnList = "status")
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminSyncJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_user_id", nullable = false)
    private AppUser adminUser;

    @Column(nullable = false, length = 40)
    private String task;

    @Column(length = 30)
    private String targetType;

    private Long targetId;

    private Integer season;

    @Column(length = 500)
    private String details;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AdminSyncJobStatus status;

    private int totalUnits;
    private int processedUnits;
    private int successfulUnits;
    private int failedUnits;
    private int savedCount;

    @Column(length = 50)
    private String phase;

    @Column(length = 30)
    private String unitLabel;

    @Column(length = 1000)
    private String message;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    public static AdminSyncJob queued(AppUser admin, String task, String targetType, Long targetId,
                                      Integer season, String details) {
        return AdminSyncJob.builder()
                .adminUser(admin)
                .task(task)
                .targetType(targetType)
                .targetId(targetId)
                .season(season)
                .details(details)
                .status(AdminSyncJobStatus.QUEUED)
                .message(task + " sync queued.")
                .createdAt(LocalDateTime.now())
                .build();
    }

    public boolean markRunning() {
        if (status != AdminSyncJobStatus.QUEUED) {
            return false;
        }
        status = AdminSyncJobStatus.RUNNING;
        startedAt = LocalDateTime.now();
        message = task + " sync is running.";
        return true;
    }

    public void beginPhase(String phase, int totalUnits, String unitLabel, int savedCount) {
        this.phase = phase;
        updateProgress(totalUnits, unitLabel, 0, 0, 0, savedCount);
        message = task + " sync phase=" + phase;
    }

    public void updateProgress(int totalUnits, String unitLabel, int processedUnits,
                               int successfulUnits, int failedUnits, int savedCount) {
        this.totalUnits = Math.max(0, totalUnits);
        this.unitLabel = unitLabel;
        this.processedUnits = Math.max(0, processedUnits);
        this.successfulUnits = Math.max(0, successfulUnits);
        this.failedUnits = Math.max(0, failedUnits);
        this.savedCount = Math.max(0, savedCount);
    }

    public void markSucceeded(int count, boolean hasErrors) {
        if (status == AdminSyncJobStatus.CANCEL_REQUESTED) {
            markCancelled();
            return;
        }
        status = hasErrors ? AdminSyncJobStatus.PARTIAL_FAILED : AdminSyncJobStatus.SUCCEEDED;
        savedCount = Math.max(savedCount, count);
        if (totalUnits > 0 && processedUnits < totalUnits) {
            processedUnits = totalUnits;
        }
        message = status == AdminSyncJobStatus.SUCCEEDED
                ? task + " sync completed. count=" + savedCount
                : task + " sync completed with failures. count=" + savedCount + "; failedUnits=" + failedUnits;
        completedAt = LocalDateTime.now();
    }

    public void markFailed(String failureMessage) {
        status = savedCount > 0 || successfulUnits > 0
                ? AdminSyncJobStatus.PARTIAL_FAILED
                : AdminSyncJobStatus.FAILED;
        message = failureMessage;
        completedAt = LocalDateTime.now();
    }

    public boolean requestCancel() {
        if (status == AdminSyncJobStatus.QUEUED) {
            status = AdminSyncJobStatus.CANCELLED;
            message = task + " sync was cancelled before it started.";
            completedAt = LocalDateTime.now();
            return true;
        }
        if (status == AdminSyncJobStatus.RUNNING) {
            status = AdminSyncJobStatus.CANCEL_REQUESTED;
            message = task + " sync cancellation requested. Waiting for the current unit to finish.";
        }
        return false;
    }

    public boolean isCancellationRequested() {
        return status == AdminSyncJobStatus.CANCEL_REQUESTED || status == AdminSyncJobStatus.CANCELLED;
    }

    public void markCancelled() {
        status = AdminSyncJobStatus.CANCELLED;
        message = task + " sync was cancelled.";
        completedAt = LocalDateTime.now();
    }
}
