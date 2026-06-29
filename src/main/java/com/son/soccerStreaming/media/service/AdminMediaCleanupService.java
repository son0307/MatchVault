package com.son.soccerStreaming.media.service;

import com.son.soccerStreaming.media.config.MediaProperties;
import com.son.soccerStreaming.player.repository.PlayerRepository;
import com.son.soccerStreaming.team.repository.TeamRepository;
import com.son.soccerStreaming.team.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "media.r2.enabled", havingValue = "true")
public class AdminMediaCleanupService {

    private static final String ADMIN_OBJECT_PREFIX = "admin/";
    private static final int DELETE_BATCH_SIZE = 1_000;

    private final S3Client s3Client;
    private final MediaProperties properties;
    private final PlayerRepository playerRepository;
    private final TeamRepository teamRepository;
    private final VenueRepository venueRepository;

    public CleanupResult cleanupUnusedObjects() {
        Set<String> referencedKeys = loadReferencedKeys();
        Instant deleteCandidateBefore = Instant.now().minus(
                properties.getAdminMediaCleanup().getMinimumAge());
        int scannedCount = 0;
        List<String> candidateKeys = new ArrayList<>();
        String continuationToken = null;

        do {
            ListObjectsV2Response response = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(properties.getR2().getBucket())
                    .prefix(ADMIN_OBJECT_PREFIX)
                    .continuationToken(continuationToken)
                    .build());

            for (S3Object object : response.contents()) {
                scannedCount++;
                if (isDeleteCandidate(object, referencedKeys, deleteCandidateBefore)) {
                    candidateKeys.add(object.key());
                    log.info("[ADMIN MEDIA CLEANUP] delete candidate. objectKey={}, lastModified={}, sizeBytes={}",
                            object.key(), object.lastModified(), object.size());
                }
            }

            continuationToken = response.isTruncated() ? response.nextContinuationToken() : null;
        } while (continuationToken != null);

        DeleteResult deleteResult = deleteInBatches(candidateKeys);

        log.info("[ADMIN MEDIA CLEANUP] cleanup completed. referenced={}, scanned={}, candidates={}, deleted={}, failed={}, minimumAge={}",
                referencedKeys.size(), scannedCount, candidateKeys.size(), deleteResult.deletedCount(),
                deleteResult.failedCount(), properties.getAdminMediaCleanup().getMinimumAge());
        return new CleanupResult(referencedKeys.size(), scannedCount, candidateKeys.size(),
                deleteResult.deletedCount(), deleteResult.failedCount());
    }

    private DeleteResult deleteInBatches(List<String> candidateKeys) {
        int deletedCount = 0;
        int failedCount = 0;

        for (int fromIndex = 0; fromIndex < candidateKeys.size(); fromIndex += DELETE_BATCH_SIZE) {
            List<String> batchKeys = candidateKeys.subList(
                    fromIndex, Math.min(fromIndex + DELETE_BATCH_SIZE, candidateKeys.size()));
            List<ObjectIdentifier> objects = batchKeys.stream()
                    .map(key -> ObjectIdentifier.builder().key(key).build())
                    .toList();
            try {
                DeleteObjectsResponse response = s3Client.deleteObjects(DeleteObjectsRequest.builder()
                        .bucket(properties.getR2().getBucket())
                        .delete(Delete.builder().objects(objects).quiet(true).build())
                        .build());
                int batchFailedCount = response.errors().size();
                deletedCount += batchKeys.size() - batchFailedCount;
                failedCount += batchFailedCount;
                response.errors().forEach(error -> log.error(
                        "[ADMIN MEDIA CLEANUP] object deletion failed. objectKey={}, code={}, message={}",
                        error.key(), error.code(), error.message()));
            } catch (SdkException e) {
                failedCount += batchKeys.size();
                log.error("[ADMIN MEDIA CLEANUP] batch deletion failed. objectCount={}", batchKeys.size(), e);
            }
        }
        return new DeleteResult(deletedCount, failedCount);
    }

    private Set<String> loadReferencedKeys() {
        Set<String> referencedKeys = new HashSet<>();
        referencedKeys.addAll(playerRepository.findAllAdminPhotoObjectKeys());
        referencedKeys.addAll(teamRepository.findAllAdminLogoObjectKeys());
        referencedKeys.addAll(venueRepository.findAllAdminVenueImageObjectKeys());
        referencedKeys.removeIf(key -> key == null || key.isBlank());
        return referencedKeys;
    }

    private boolean isDeleteCandidate(S3Object object, Set<String> referencedKeys, Instant deleteCandidateBefore) {
        return object.key() != null
                && !referencedKeys.contains(object.key())
                && object.lastModified() != null
                && object.lastModified().isBefore(deleteCandidateBefore);
    }

    public record CleanupResult(int referencedCount, int scannedCount, int candidateCount,
                                int deletedCount, int failedCount) {
    }

    private record DeleteResult(int deletedCount, int failedCount) {
    }
}
