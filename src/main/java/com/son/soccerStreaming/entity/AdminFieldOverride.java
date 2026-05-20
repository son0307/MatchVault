package com.son.soccerStreaming.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@Table(name = "admin_field_override", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"target_type", "target_id", "field_name"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminFieldOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private AdminOverrideTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "field_name", nullable = false, length = 80)
    private String fieldName;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static AdminFieldOverride of(AdminOverrideTargetType targetType, Long targetId, String fieldName) {
        return AdminFieldOverride.builder()
                .targetType(targetType)
                .targetId(targetId)
                .fieldName(fieldName)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
