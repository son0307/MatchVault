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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_user_id")
    private AppUser adminUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AdminAuditType type;

    @Column(length = 30)
    private String targetType;

    private Long targetId;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(length = 4000)
    private String details;

    @Column(nullable = false)
    private boolean success;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static AdminAuditLog of(AppUser adminUser, AdminAuditType type, String targetType,
                                   Long targetId, String message, boolean success) {
        return of(adminUser, type, targetType, targetId, message, null, success);
    }

    public static AdminAuditLog of(AppUser adminUser, AdminAuditType type, String targetType,
                                   Long targetId, String message, String details, boolean success) {
        return AdminAuditLog.builder()
                .adminUser(adminUser)
                .type(type)
                .targetType(targetType)
                .targetId(targetId)
                .message(message)
                .details(details)
                .success(success)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
