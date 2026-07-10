package com.son.soccerStreaming.apifootball.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@Table(name = "api_football_sync_status")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class ApiFootballSyncStatus {

    @Id
    @Column(nullable = false, length = 60)
    private String syncKey;

    @Column(nullable = false, length = 120)
    private String displayName;

    @Column(nullable = false)
    private LocalDateTime lastSyncedAt;

    private LocalDateTime lastAttemptAt;

    private LocalDateTime lastSuccessAt;

    private LocalDateTime lastFailureAt;

    private Integer failureCount;

    @Column(length = 1000)
    private String lastErrorMessage;

    @Column(length = 30)
    private String status;

    public void recordAttempt(String displayName, LocalDateTime attemptedAt) {
        this.displayName = displayName;
        this.lastAttemptAt = attemptedAt;
        if (this.lastSyncedAt == null) {
            this.lastSyncedAt = attemptedAt;
        }
        if (this.status == null) {
            this.status = ApiFootballSyncState.NEVER_SYNCED.name();
        }
    }

    public void recordSuccess(String displayName, LocalDateTime lastSyncedAt) {
        this.displayName = displayName;
        this.lastSyncedAt = lastSyncedAt;
        this.lastAttemptAt = lastSyncedAt;
        this.lastSuccessAt = lastSyncedAt;
        this.failureCount = 0;
        this.lastErrorMessage = null;
        this.status = ApiFootballSyncState.OK.name();
    }

    public void recordFailure(String displayName, LocalDateTime failedAt, String errorMessage) {
        this.displayName = displayName;
        this.lastAttemptAt = failedAt;
        this.lastFailureAt = failedAt;
        this.failureCount = (this.failureCount == null ? 0 : this.failureCount) + 1;
        this.lastErrorMessage = errorMessage;
        this.status = ApiFootballSyncState.FAILED.name();
    }

    public void recordRetryPending(String displayName, LocalDateTime scheduledAt, String errorMessage) {
        this.displayName = displayName;
        this.lastAttemptAt = scheduledAt;
        this.lastFailureAt = scheduledAt;
        this.failureCount = Math.max(1, this.failureCount == null ? 0 : this.failureCount);
        this.lastErrorMessage = errorMessage;
        this.status = ApiFootballSyncState.RETRY_PENDING.name();
    }
}
