package com.son.soccerStreaming.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "admin_sync_job_error", indexes = {
        @Index(name = "idx_admin_sync_job_error_job", columnList = "job_id")
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminSyncJobError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private AdminSyncJob job;

    @Column(nullable = false, length = 30)
    private String unitType;

    @Column(length = 200)
    private String unitId;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static AdminSyncJobError of(AdminSyncJob job, String unitType, String unitId, String message) {
        return AdminSyncJobError.builder()
                .job(job)
                .unitType(unitType)
                .unitId(unitId)
                .message(message)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
