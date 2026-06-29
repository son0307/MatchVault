package com.son.soccerStreaming.admin.dto;

import com.son.soccerStreaming.admin.entity.AdminMediaTargetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

public class AdminMediaDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PresignRequest {
        private AdminMediaTargetType targetType;
        private Long targetId;
        private String contentType;
        private Long sizeBytes;
    }

    @Getter
    @Builder
    public static class PresignResponse {
        private String objectKey;
        private String uploadUrl;
        private Map<String, String> requiredHeaders;
        private Instant expiresAt;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompleteRequest {
        private AdminMediaTargetType targetType;
        private Long targetId;
        private String objectKey;
    }

    @Getter
    @Builder
    public static class MediaResponse {
        private AdminMediaTargetType targetType;
        private Long targetId;
        private String objectKey;
        private String publicUrl;
        private boolean adminImage;
    }
}
