package com.son.soccerStreaming.admin.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

public class AdminDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamUpdateRequest {
        private String name;
        private String code;
        private String country;
        private Integer founded;
        private String logoUrl;
        private Long venueId;
        private String venueName;
        private String venueAddress;
        private String venueCity;
        private Integer capacity;
        private String surface;
        private String venueImageUrl;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlayerUpdateRequest {
        private String name;
        private String firstname;
        private String lastname;
        private Integer age;
        private LocalDate birthDate;
        private String birthPlace;
        private String birthCountry;
        private String nationality;
        private String height;
        private String weight;
        private String position;
        private Integer number;
        private String photoUrl;
    }

    @Getter
    @Builder
    public static class TeamAdminResponse {
        private Long teamId;
        private String name;
        private String code;
        private String country;
        private Integer founded;
        private String logoUrl;
        private Long venueId;
        private String venueName;
        private String venueAddress;
        private String venueCity;
        private Integer capacity;
        private String surface;
        private String venueImageUrl;
        private List<ManualOverrideResponse> manualOverrides;
    }

    @Getter
    @Builder
    public static class PlayerAdminResponse {
        private Long playerId;
        private String name;
        private String firstname;
        private String lastname;
        private Integer age;
        private LocalDate birthDate;
        private String birthPlace;
        private String birthCountry;
        private String nationality;
        private String height;
        private String weight;
        private String position;
        private Integer number;
        private String photoUrl;
        private List<ManualOverrideResponse> manualOverrides;
    }

    @Getter
    @Builder
    public static class ManualOverrideResponse {
        private String fieldName;
        private LocalDateTime updatedAt;
    }

    @Getter
    @Builder
    public static class SyncResponse {
        private String task;
        private boolean success;
        private boolean queued;
        private int count;
        private String message;
    }

    @Getter
    @Builder
    public static class AuditLogResponse {
        private Long id;
        private String adminEmail;
        private String type;
        private String targetType;
        private Long targetId;
        private String message;
        private String details;
        private boolean success;
        private LocalDateTime createdAt;
    }

    @Getter
    @Builder
    public static class AuditLogListResponse {
        private List<AuditLogResponse> logs;
    }

    @Getter
    @Builder
    public static class SyncStatusResponse {
        private List<SyncStatusItem> statuses;
    }

    @Getter
    @Builder
    public static class SyncStatusItem {
        private String task;
        private String label;
        private OffsetDateTime lastSyncedAt;
    }
}
