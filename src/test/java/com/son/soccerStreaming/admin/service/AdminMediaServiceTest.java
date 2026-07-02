package com.son.soccerStreaming.admin.service;

import com.son.soccerStreaming.admin.dto.AdminMediaDto;
import com.son.soccerStreaming.admin.entity.AdminAuditLog;
import com.son.soccerStreaming.admin.entity.AdminMediaTargetType;
import com.son.soccerStreaming.admin.repository.AdminAuditLogRepository;
import com.son.soccerStreaming.auth.entity.AppUser;
import com.son.soccerStreaming.auth.repository.AppUserRepository;
import com.son.soccerStreaming.global.exception.CustomException;
import com.son.soccerStreaming.global.exception.ErrorCode;
import com.son.soccerStreaming.media.config.MediaProperties;
import com.son.soccerStreaming.media.service.MediaUrlService;
import com.son.soccerStreaming.player.entity.Player;
import com.son.soccerStreaming.player.repository.PlayerRepository;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.team.repository.TeamRepository;
import com.son.soccerStreaming.team.repository.VenueRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminMediaServiceTest {

    private PlayerRepository playerRepository;
    private TeamRepository teamRepository;
    private VenueRepository venueRepository;
    private AppUserRepository appUserRepository;
    private AdminAuditLogRepository auditLogRepository;
    private S3Client s3Client;
    private S3Presigner presigner;
    private MediaProperties properties;
    private AdminMediaService service;

    @BeforeEach
    void setUp() {
        playerRepository = mock(PlayerRepository.class);
        teamRepository = mock(TeamRepository.class);
        venueRepository = mock(VenueRepository.class);
        appUserRepository = mock(AppUserRepository.class);
        auditLogRepository = mock(AdminAuditLogRepository.class);
        s3Client = mock(S3Client.class);
        presigner = S3Presigner.builder()
                .endpointOverride(URI.create("https://r2.example.com"))
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("key", "secret")))
                .build();
        properties = new MediaProperties();
        properties.setPublicBaseUrl("https://media.example.com");
        properties.getR2().setEnabled(true);
        properties.getR2().setBucket("media");

        ObjectProvider<S3Client> clientProvider = provider(s3Client);
        ObjectProvider<S3Presigner> presignerProvider = provider(presigner);
        service = new AdminMediaService(
                playerRepository,
                teamRepository,
                venueRepository,
                appUserRepository,
                auditLogRepository,
                new MediaUrlService(properties),
                properties,
                clientProvider,
                presignerProvider
        );
    }

    @AfterEach
    void tearDown() {
        presigner.close();
    }

    @Test
    void presignCreatesTargetScopedKey() {
        Team team = Team.builder().teamId(47L).name("Tottenham").build();
        when(teamRepository.findByTeamId(47L)).thenReturn(Optional.of(team));

        AdminMediaDto.PresignResponse response = service.presign(new AdminMediaDto.PresignRequest(
                AdminMediaTargetType.TEAM_LOGO, 47L, "image/png", 1024L));

        assertThat(response.getObjectKey()).startsWith("admin/team-logos/47/").endsWith(".png");
        assertThat(response.getUploadUrl())
                .startsWith("https://")
                .contains("/admin/team-logos/47/")
                .contains("X-Amz-Expires=300");
        assertThat(response.getRequiredHeaders()).containsEntry("Content-Type", "image/png");
    }

    @Test
    void presignRejectsOversizedFile() {
        AdminMediaDto.PresignRequest request = new AdminMediaDto.PresignRequest(
                AdminMediaTargetType.PLAYER_PHOTO, 7L, "image/png", 2L * 1024 * 1024 + 1);

        assertThatThrownBy(() -> service.presign(request))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_ADMIN_MEDIA_REQUEST));
    }

    @Test
    void completeStoresAdminKeyAndAuditLog() {
        Player player = Player.builder()
                .playerId(7L)
                .name("Son")
                .photoUrl("https://media.api-sports.io/football/players/7.png")
                .build();
        AppUser admin = mock(AppUser.class);
        String objectKey = "admin/player-photos/7/123e4567-e89b-12d3-a456-426614174000.png";
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentType("image/png").contentLength(1024L).build());
        when(playerRepository.findByPlayerId(7L)).thenReturn(Optional.of(player));
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(admin));

        AdminMediaDto.MediaResponse response = service.complete(1L,
                new AdminMediaDto.CompleteRequest(AdminMediaTargetType.PLAYER_PHOTO, 7L, objectKey));

        assertThat(player.getAdminPhotoObjectKey()).isEqualTo(objectKey);
        assertThat(response.getPublicUrl()).isEqualTo("https://media.example.com/" + objectKey);
        assertThat(response.isAdminImage()).isTrue();
        verify(auditLogRepository).save(any(AdminAuditLog.class));
    }

    @Test
    void completeRejectsKeyForDifferentTarget() {
        String objectKey = "admin/player-photos/8/123e4567-e89b-12d3-a456-426614174000.png";

        assertThatThrownBy(() -> service.complete(1L,
                new AdminMediaDto.CompleteRequest(AdminMediaTargetType.PLAYER_PHOTO, 7L, objectKey)))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_ADMIN_MEDIA_OBJECT));
    }

    @Test
    void completePreservesStorageFailureCause() {
        String objectKey = "admin/player-photos/7/123e4567-e89b-12d3-a456-426614174000.png";
        SdkClientException cause = SdkClientException.create("storage unavailable");
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(cause);

        assertThatThrownBy(() -> service.complete(1L,
                new AdminMediaDto.CompleteRequest(AdminMediaTargetType.PLAYER_PHOTO, 7L, objectKey)))
                .isInstanceOfSatisfying(CustomException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ADMIN_MEDIA_STORAGE_UNAVAILABLE);
                    assertThat(exception.getCause()).isSameAs(cause);
                });
    }

    @Test
    void restoreClearsOnlyAdminKeyAndFallsBackToCachedImage() {
        Player player = Player.builder()
                .playerId(7L)
                .name("Son")
                .photoUrl("https://media.api-sports.io/football/players/7.png")
                .build();
        player.markPhotoCached("api-football/players/7.png", LocalDateTime.now());
        player.updateAdminPhotoObjectKey("admin/player-photos/7/custom.png");
        AppUser admin = mock(AppUser.class);
        when(playerRepository.findByPlayerId(7L)).thenReturn(Optional.of(player));
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(admin));

        AdminMediaDto.MediaResponse response = service.restore(1L, AdminMediaTargetType.PLAYER_PHOTO, 7L);

        assertThat(player.getAdminPhotoObjectKey()).isNull();
        assertThat(player.getPhotoObjectKey()).isEqualTo("api-football/players/7.png");
        assertThat(response.getPublicUrl()).isEqualTo("https://media.example.com/api-football/players/7.png");
        assertThat(response.isAdminImage()).isFalse();
    }

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
