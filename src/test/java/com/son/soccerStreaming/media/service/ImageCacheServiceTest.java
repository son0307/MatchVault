package com.son.soccerStreaming.media.service;

import com.son.soccerStreaming.media.config.MediaProperties;
import com.son.soccerStreaming.player.entity.Player;
import com.son.soccerStreaming.player.repository.PlayerRepository;
import com.son.soccerStreaming.team.repository.TeamRepository;
import com.son.soccerStreaming.team.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImageCacheServiceTest {

    private PlayerRepository playerRepository;
    private ImageDownloader imageDownloader;
    private ImageStorageService imageStorageService;
    private ObjectProvider<ImageStorageService> imageStorageServiceProvider;
    private MediaProperties properties;
    private ImageCacheService imageCacheService;

    @BeforeEach
    void setUp() {
        playerRepository = mock(PlayerRepository.class);
        imageDownloader = mock(ImageDownloader.class);
        imageStorageService = mock(ImageStorageService.class);
        imageStorageServiceProvider = mock(ObjectProvider.class);
        properties = new MediaProperties();
        properties.getCache().setEnabled(true);

        imageCacheService = new ImageCacheService(
                playerRepository,
                mock(TeamRepository.class),
                mock(VenueRepository.class),
                imageDownloader,
                imageStorageServiceProvider,
                properties,
                transactionManager()
        );
    }

    @Test
    void requestPlayerPhotoCacheUploadsAndStoresObjectKey() {
        Player player = Player.builder()
                .playerId(7L)
                .name("Son")
                .photoUrl("https://media.api-sports.io/football/players/7.png")
                .build();

        when(playerRepository.findByPlayerId(7L)).thenReturn(Optional.of(player));
        when(imageStorageServiceProvider.getIfAvailable()).thenReturn(imageStorageService);
        when(imageDownloader.download(player.getPhotoUrl()))
                .thenReturn(new ImageDownloadResult(new byte[]{1, 2, 3}, "image/png", "png"));

        imageCacheService.requestPlayerPhotoCache(player);

        verify(imageStorageService).upload(
                "api-football/players/7.png",
                new byte[]{1, 2, 3},
                "image/png"
        );
        assertThat(player.getPhotoObjectKey()).isEqualTo("api-football/players/7.png");
        assertThat(player.getPhotoCachedAt()).isNotNull();
        assertThat(player.getPhotoCacheFailedAt()).isNull();
    }

    @Test
    void requestPlayerPhotoCacheSkipsRecentFailure() {
        Player player = Player.builder()
                .playerId(7L)
                .name("Son")
                .photoUrl("https://media.api-sports.io/football/players/7.png")
                .build();
        player.markPhotoCacheFailed(LocalDateTime.now(ZoneOffset.UTC), "temporary failure");

        imageCacheService.requestPlayerPhotoCache(player);

        verify(imageDownloader, never()).download(player.getPhotoUrl());
        verify(imageStorageService, never()).upload(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    private PlatformTransactionManager transactionManager() {
        return new AbstractPlatformTransactionManager() {
            @Override
            protected Object doGetTransaction() {
                return new Object();
            }

            @Override
            protected void doBegin(Object transaction, TransactionDefinition definition) {
            }

            @Override
            protected void doCommit(DefaultTransactionStatus status) {
            }

            @Override
            protected void doRollback(DefaultTransactionStatus status) {
            }
        };
    }
}
