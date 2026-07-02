package com.son.soccerStreaming.admin.service;

import com.son.soccerStreaming.admin.dto.AdminMediaDto;
import com.son.soccerStreaming.admin.entity.AdminAuditLog;
import com.son.soccerStreaming.admin.entity.AdminAuditType;
import com.son.soccerStreaming.admin.entity.AdminMediaTargetType;
import com.son.soccerStreaming.admin.repository.AdminAuditLogRepository;
import com.son.soccerStreaming.auth.entity.AppUser;
import com.son.soccerStreaming.auth.repository.AppUserRepository;
import com.son.soccerStreaming.global.exception.CustomException;
import com.son.soccerStreaming.global.exception.ErrorCode;
import com.son.soccerStreaming.global.config.RedisCacheConfig;
import com.son.soccerStreaming.media.config.MediaProperties;
import com.son.soccerStreaming.media.service.MediaUrlService;
import com.son.soccerStreaming.player.entity.Player;
import com.son.soccerStreaming.player.repository.PlayerRepository;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.team.entity.Venue;
import com.son.soccerStreaming.team.repository.TeamRepository;
import com.son.soccerStreaming.team.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminMediaService {

    private static final long MAX_IMAGE_BYTES = 2 * 1024 * 1024;
    private static final Duration PRESIGN_DURATION = Duration.ofMinutes(5);
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/png", "image/jpeg", "image/webp");
    private static final Pattern ADMIN_OBJECT_SUFFIX = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpg|webp)"
    );

    private final PlayerRepository playerRepository;
    private final TeamRepository teamRepository;
    private final VenueRepository venueRepository;
    private final AppUserRepository appUserRepository;
    private final AdminAuditLogRepository adminAuditLogRepository;
    private final MediaUrlService mediaUrlService;
    private final MediaProperties properties;
    private final ObjectProvider<S3Client> s3ClientProvider;
    private final ObjectProvider<S3Presigner> s3PresignerProvider;

    public AdminMediaDto.PresignResponse presign(AdminMediaDto.PresignRequest request) {
        validateRequest(request);
        ensureTargetExists(request.getTargetType(), request.getTargetId());

        S3Presigner presigner = s3PresignerProvider.getIfAvailable();
        if (!properties.getR2().isEnabled() || presigner == null) {
            throw new CustomException(ErrorCode.ADMIN_MEDIA_STORAGE_UNAVAILABLE);
        }

        String contentType = normalizeContentType(request.getContentType());
        String objectKey = request.getTargetType().objectKeyPrefix(request.getTargetId())
                + UUID.randomUUID() + "." + extension(contentType);
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(properties.getR2().getBucket())
                .key(objectKey)
                .contentType(contentType)
                .build();
        PresignedPutObjectRequest presigned;
        try {
            presigned = presigner.presignPutObject(PutObjectPresignRequest.builder()
                    .signatureDuration(PRESIGN_DURATION)
                    .putObjectRequest(putObjectRequest)
                    .build());
        } catch (SdkException e) {
            throw new CustomException(ErrorCode.ADMIN_MEDIA_STORAGE_UNAVAILABLE, e);
        }

        Instant expiresAt = Instant.now().plus(PRESIGN_DURATION);
        return AdminMediaDto.PresignResponse.builder()
                .objectKey(objectKey)
                .uploadUrl(presigned.url().toString())
                .requiredHeaders(Map.of("Content-Type", contentType))
                .expiresAt(expiresAt)
                .build();
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = {RedisCacheConfig.FAVORITE_TEAM_CARD_CACHE,
                    RedisCacheConfig.FAVORITE_PLAYER_CARD_CACHE}, allEntries = true),
            @CacheEvict(cacheManager = RedisCacheConfig.RANKINGS_CACHE_MANAGER,
                    cacheNames = {RedisCacheConfig.TEAM_PLAYER_RANKINGS_CACHE,
                            RedisCacheConfig.LEAGUE_PLAYER_RANKINGS_CACHE,
                            RedisCacheConfig.LEAGUE_TEAM_RANKINGS_CACHE}, allEntries = true)
    })
    public AdminMediaDto.MediaResponse complete(Long adminUserId, AdminMediaDto.CompleteRequest request) {
        if (request == null || request.getTargetType() == null || request.getTargetId() == null
                || request.getTargetId() <= 0 || request.getObjectKey() == null || request.getObjectKey().isBlank()) {
            throw new CustomException(ErrorCode.INVALID_ADMIN_MEDIA_REQUEST);
        }
        validateObjectKey(request.getTargetType(), request.getTargetId(), request.getObjectKey());
        HeadObjectResponse head = headObject(request.getObjectKey());
        validateUploadedObject(request.getObjectKey(), head);

        String publicUrl = switch (request.getTargetType()) {
            case PLAYER_PHOTO -> updatePlayerImage(request.getTargetId(), request.getObjectKey());
            case TEAM_LOGO -> updateTeamImage(request.getTargetId(), request.getObjectKey());
            case VENUE_IMAGE -> updateVenueImage(request.getTargetId(), request.getObjectKey());
        };
        saveAudit(adminUserId, AdminAuditType.MEDIA_UPLOAD, request.getTargetType(), request.getTargetId(),
                "Admin image uploaded", request.getObjectKey());
        return mediaResponse(request.getTargetType(), request.getTargetId(), request.getObjectKey(), publicUrl, true);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = {RedisCacheConfig.FAVORITE_TEAM_CARD_CACHE,
                    RedisCacheConfig.FAVORITE_PLAYER_CARD_CACHE}, allEntries = true),
            @CacheEvict(cacheManager = RedisCacheConfig.RANKINGS_CACHE_MANAGER,
                    cacheNames = {RedisCacheConfig.TEAM_PLAYER_RANKINGS_CACHE,
                            RedisCacheConfig.LEAGUE_PLAYER_RANKINGS_CACHE,
                            RedisCacheConfig.LEAGUE_TEAM_RANKINGS_CACHE}, allEntries = true)
    })
    public AdminMediaDto.MediaResponse restore(Long adminUserId, AdminMediaTargetType targetType, Long targetId) {
        if (targetType == null || targetId == null || targetId <= 0) {
            throw new CustomException(ErrorCode.INVALID_ADMIN_MEDIA_REQUEST);
        }
        String publicUrl = switch (targetType) {
            case PLAYER_PHOTO -> restorePlayerImage(targetId);
            case TEAM_LOGO -> restoreTeamImage(targetId);
            case VENUE_IMAGE -> restoreVenueImage(targetId);
        };
        saveAudit(adminUserId, AdminAuditType.MEDIA_RESTORE, targetType, targetId,
                "Admin image restored to fallback", null);
        return mediaResponse(targetType, targetId, null, publicUrl, false);
    }

    private void validateRequest(AdminMediaDto.PresignRequest request) {
        if (request == null || request.getTargetType() == null || request.getTargetId() == null
                || request.getTargetId() <= 0 || request.getSizeBytes() == null
                || request.getSizeBytes() <= 0 || request.getSizeBytes() > MAX_IMAGE_BYTES) {
            throw new CustomException(ErrorCode.INVALID_ADMIN_MEDIA_REQUEST);
        }
        normalizeContentType(request.getContentType());
    }

    private String normalizeContentType(String contentType) {
        String normalized = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_CONTENT_TYPES.contains(normalized)) {
            throw new CustomException(ErrorCode.INVALID_ADMIN_MEDIA_REQUEST);
        }
        return normalized;
    }

    private String extension(String contentType) {
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            default -> throw new CustomException(ErrorCode.INVALID_ADMIN_MEDIA_REQUEST);
        };
    }

    private void ensureTargetExists(AdminMediaTargetType targetType, Long targetId) {
        switch (targetType) {
            case PLAYER_PHOTO -> playerRepository.findByPlayerId(targetId)
                    .orElseThrow(() -> new CustomException(ErrorCode.PLAYER_NOT_FOUND));
            case TEAM_LOGO -> teamRepository.findByTeamId(targetId)
                    .orElseThrow(() -> new CustomException(ErrorCode.TEAM_NOT_FOUND));
            case VENUE_IMAGE -> venueRepository.findByVenueId(targetId)
                    .orElseThrow(() -> new CustomException(ErrorCode.VENUE_NOT_FOUND));
        }
    }

    private void validateObjectKey(AdminMediaTargetType targetType, Long targetId, String objectKey) {
        String prefix = targetType.objectKeyPrefix(targetId);
        if (!objectKey.startsWith(prefix)
                || !ADMIN_OBJECT_SUFFIX.matcher(objectKey.substring(prefix.length())).matches()) {
            throw new CustomException(ErrorCode.INVALID_ADMIN_MEDIA_OBJECT);
        }
    }

    private HeadObjectResponse headObject(String objectKey) {
        S3Client client = s3ClientProvider.getIfAvailable();
        if (!properties.getR2().isEnabled() || client == null) {
            throw new CustomException(ErrorCode.ADMIN_MEDIA_STORAGE_UNAVAILABLE);
        }
        try {
            return client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.getR2().getBucket())
                    .key(objectKey)
                    .build());
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new CustomException(ErrorCode.ADMIN_MEDIA_OBJECT_NOT_FOUND);
            }
            throw new CustomException(ErrorCode.ADMIN_MEDIA_STORAGE_UNAVAILABLE, e);
        } catch (SdkException e) {
            throw new CustomException(ErrorCode.ADMIN_MEDIA_STORAGE_UNAVAILABLE, e);
        }
    }

    private void validateUploadedObject(String objectKey, HeadObjectResponse head) {
        String contentType = normalizeContentTypeForObject(head.contentType());
        if (head.contentLength() == null || head.contentLength() <= 0 || head.contentLength() > MAX_IMAGE_BYTES
                || !objectKey.endsWith("." + extension(contentType))) {
            throw new CustomException(ErrorCode.INVALID_ADMIN_MEDIA_OBJECT);
        }
    }

    private String normalizeContentTypeForObject(String contentType) {
        try {
            return normalizeContentType(contentType);
        } catch (CustomException e) {
            throw new CustomException(ErrorCode.INVALID_ADMIN_MEDIA_OBJECT);
        }
    }

    private String updatePlayerImage(Long playerId, String objectKey) {
        Player player = playerRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new CustomException(ErrorCode.PLAYER_NOT_FOUND));
        player.updateAdminPhotoObjectKey(objectKey);
        return mediaUrlService.playerPhotoUrl(player);
    }

    private String updateTeamImage(Long teamId, String objectKey) {
        Team team = teamRepository.findByTeamId(teamId)
                .orElseThrow(() -> new CustomException(ErrorCode.TEAM_NOT_FOUND));
        team.updateAdminLogoObjectKey(objectKey);
        return mediaUrlService.teamLogoUrl(team);
    }

    private String updateVenueImage(Long venueId, String objectKey) {
        Venue venue = venueRepository.findByVenueId(venueId)
                .orElseThrow(() -> new CustomException(ErrorCode.VENUE_NOT_FOUND));
        venue.updateAdminVenueImageObjectKey(objectKey);
        return mediaUrlService.venueImageUrl(venue);
    }

    private String restorePlayerImage(Long playerId) {
        Player player = playerRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new CustomException(ErrorCode.PLAYER_NOT_FOUND));
        player.clearAdminPhotoObjectKey();
        return mediaUrlService.playerPhotoUrl(player);
    }

    private String restoreTeamImage(Long teamId) {
        Team team = teamRepository.findByTeamId(teamId)
                .orElseThrow(() -> new CustomException(ErrorCode.TEAM_NOT_FOUND));
        team.clearAdminLogoObjectKey();
        return mediaUrlService.teamLogoUrl(team);
    }

    private String restoreVenueImage(Long venueId) {
        Venue venue = venueRepository.findByVenueId(venueId)
                .orElseThrow(() -> new CustomException(ErrorCode.VENUE_NOT_FOUND));
        venue.clearAdminVenueImageObjectKey();
        return mediaUrlService.venueImageUrl(venue);
    }

    private void saveAudit(Long adminUserId, AdminAuditType auditType, AdminMediaTargetType targetType,
                           Long targetId, String message, String details) {
        AppUser admin = appUserRepository.findById(adminUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        adminAuditLogRepository.save(AdminAuditLog.of(
                admin, auditType, targetType.name(), targetId, message, details, true));
    }

    private AdminMediaDto.MediaResponse mediaResponse(AdminMediaTargetType targetType, Long targetId,
                                                       String objectKey, String publicUrl, boolean adminImage) {
        return AdminMediaDto.MediaResponse.builder()
                .targetType(targetType)
                .targetId(targetId)
                .objectKey(objectKey)
                .publicUrl(publicUrl)
                .adminImage(adminImage)
                .build();
    }
}
