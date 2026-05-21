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

    public void recordSuccess(String displayName, LocalDateTime lastSyncedAt) {
        this.displayName = displayName;
        this.lastSyncedAt = lastSyncedAt;
    }
}
