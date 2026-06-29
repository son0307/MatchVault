package com.son.soccerStreaming.media.service;

import com.son.soccerStreaming.media.config.MediaProperties;
import com.son.soccerStreaming.player.repository.PlayerRepository;
import com.son.soccerStreaming.team.repository.TeamRepository;
import com.son.soccerStreaming.team.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Error;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminMediaCleanupServiceTest {

    private S3Client s3Client;
    private PlayerRepository playerRepository;
    private TeamRepository teamRepository;
    private VenueRepository venueRepository;
    private MediaProperties properties;
    private AdminMediaCleanupService service;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        playerRepository = mock(PlayerRepository.class);
        teamRepository = mock(TeamRepository.class);
        venueRepository = mock(VenueRepository.class);
        properties = new MediaProperties();
        properties.getR2().setBucket("media");
        properties.getAdminMediaCleanup().setMinimumAge(Duration.ofHours(1));
        service = new AdminMediaCleanupService(
                s3Client, properties, playerRepository, teamRepository, venueRepository);

        when(playerRepository.findAllAdminPhotoObjectKeys()).thenReturn(List.of());
        when(teamRepository.findAllAdminLogoObjectKeys()).thenReturn(List.of());
        when(venueRepository.findAllAdminVenueImageObjectKeys()).thenReturn(List.of());
        when(s3Client.deleteObjects(any(DeleteObjectsRequest.class)))
                .thenReturn(DeleteObjectsResponse.builder().build());
    }

    @Test
    void findsOnlyOldUnreferencedAdminObjects() {
        String referencedKey = "admin/player-photos/1/referenced.png";
        when(playerRepository.findAllAdminPhotoObjectKeys()).thenReturn(List.of(referencedKey));
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(ListObjectsV2Response.builder()
                .isTruncated(false)
                .contents(
                        object(referencedKey, Instant.now().minus(Duration.ofDays(3))),
                        object("admin/player-photos/1/orphan.png", Instant.now().minus(Duration.ofDays(3))),
                        object("admin/player-photos/1/uploading.png", Instant.now().minus(Duration.ofMinutes(5))))
                .build());

        AdminMediaCleanupService.CleanupResult result = service.cleanupUnusedObjects();

        assertThat(result.referencedCount()).isEqualTo(1);
        assertThat(result.scannedCount()).isEqualTo(3);
        assertThat(result.candidateCount()).isEqualTo(1);
        assertThat(result.deletedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isZero();
        verify(playerRepository, times(1)).findAllAdminPhotoObjectKeys();
        verify(teamRepository, times(1)).findAllAdminLogoObjectKeys();
        verify(venueRepository, times(1)).findAllAdminVenueImageObjectKeys();

        ArgumentCaptor<DeleteObjectsRequest> requestCaptor = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client).deleteObjects(requestCaptor.capture());
        assertThat(requestCaptor.getValue().delete().objects())
                .extracting(object -> object.key())
                .containsExactly("admin/player-photos/1/orphan.png");
    }

    @Test
    void reportsPartialDeleteFailuresForNextRunRetry() {
        String objectKey = "admin/team-logos/1/orphan.png";
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(ListObjectsV2Response.builder()
                .isTruncated(false)
                .contents(object(objectKey, Instant.now().minus(Duration.ofDays(3))))
                .build());
        when(s3Client.deleteObjects(any(DeleteObjectsRequest.class))).thenReturn(DeleteObjectsResponse.builder()
                .errors(S3Error.builder().key(objectKey).code("AccessDenied").message("denied").build())
                .build());

        AdminMediaCleanupService.CleanupResult result = service.cleanupUnusedObjects();

        assertThat(result.deletedCount()).isZero();
        assertThat(result.failedCount()).isEqualTo(1);
    }

    private S3Object object(String key, Instant lastModified) {
        return S3Object.builder()
                .key(key)
                .lastModified(lastModified)
                .size(1024L)
                .build();
    }
}
