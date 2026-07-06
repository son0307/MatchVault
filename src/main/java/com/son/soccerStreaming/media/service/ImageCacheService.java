package com.son.soccerStreaming.media.service;

import com.son.soccerStreaming.media.config.MediaProperties;
import com.son.soccerStreaming.player.entity.Player;
import com.son.soccerStreaming.player.repository.PlayerRepository;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.team.entity.Venue;
import com.son.soccerStreaming.team.repository.TeamRepository;
import com.son.soccerStreaming.team.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageCacheService {

    private static final String FAILURE_STORAGE_NOT_CONFIGURED = "STORAGE_NOT_CONFIGURED";
    private static final String FAILURE_DOWNLOAD_FAILED = "DOWNLOAD_FAILED";
    private static final String FAILURE_CACHE_FAILED = "CACHE_FAILED";

    private final PlayerRepository playerRepository;
    private final TeamRepository teamRepository;
    private final VenueRepository venueRepository;
    private final ImageDownloader imageDownloader;
    private final ObjectProvider<ImageStorageService> imageStorageServiceProvider;
    private final MediaProperties properties;
    private final PlatformTransactionManager transactionManager;

    public void requestPlayerPhotoCache(Player player) {
        if (player == null || player.getPlayerId() == null || !shouldRequest(player.getPhotoUrl(),
                player.getPhotoObjectKey(), player.getPhotoCacheFailedAt())) {
            return;
        }
        afterCommit(() -> cachePlayerPhoto(player.getPlayerId()));
    }

    public void requestTeamLogoCache(Team team) {
        if (team == null || team.getTeamId() == null || !shouldRequest(team.getLogoUrl(),
                team.getLogoObjectKey(), team.getLogoCacheFailedAt())) {
            return;
        }
        afterCommit(() -> cacheTeamLogo(team.getTeamId()));
    }

    public void requestVenueImageCache(Venue venue) {
        if (venue == null || venue.getVenueId() == null || !shouldRequest(venue.getVenueImageUrl(),
                venue.getVenueImageObjectKey(), venue.getVenueImageCacheFailedAt())) {
            return;
        }
        afterCommit(() -> cacheVenueImage(venue.getVenueId()));
    }

    public void cacheMissingImages(int batchSize) {
        LocalDateTime retryBefore = now().minus(properties.getCache().getFailureCooldown());
        PageRequest page = PageRequest.of(0, batchSize);
        log.info("Image cache scheduler started. batchSize={}, retryBefore={}", batchSize, retryBefore);

        List<Long> playerIds = playerRepository.findPhotoCacheCandidateIds(retryBefore, page);
        List<Long> teamIds = teamRepository.findLogoCacheCandidateIds(retryBefore, page);
        List<Long> venueIds = venueRepository.findImageCacheCandidateIds(retryBefore, page);

        playerIds.forEach(this::cachePlayerPhotoByDatabaseId);
        teamIds.forEach(this::cacheTeamLogoByDatabaseId);
        venueIds.forEach(this::cacheVenueImageByDatabaseId);
        log.info("Image cache scheduler completed. players={}, teams={}, venues={}",
                playerIds.size(), teamIds.size(), venueIds.size());
    }

    public void cachePlayerPhotos(Collection<Long> playerIds) {
        if (playerIds == null || playerIds.isEmpty()) {
            return;
        }
        log.info("Player photo cache batch started. players={}", playerIds.size());
        playerIds.forEach(this::cachePlayerPhoto);
        log.info("Player photo cache batch completed. players={}", playerIds.size());
    }

    private void cachePlayerPhoto(Long playerId) {
        loadPlayerCandidate(() -> playerRepository.findByPlayerId(playerId))
                .ifPresent(candidate -> cache(
                        "player-photo",
                        candidate.externalId(),
                        candidate.sourceUrl(),
                        "api-football/players/%s".formatted(candidate.externalId()),
                        (objectKey, cachedAt) -> markPlayerCached(playerId, candidate.sourceUrl(), objectKey, cachedAt),
                        failureReason -> markPlayerFailed(playerId, candidate.sourceUrl(), failureReason)
                ));
    }

    private void cachePlayerPhotoByDatabaseId(Long id) {
        loadPlayerCandidate(() -> playerRepository.findById(id))
                .ifPresent(candidate -> cache(
                        "player-photo",
                        candidate.externalId(),
                        candidate.sourceUrl(),
                        "api-football/players/%s".formatted(candidate.externalId()),
                        (objectKey, cachedAt) -> markPlayerCachedById(id, candidate.sourceUrl(), objectKey, cachedAt),
                        failureReason -> markPlayerFailedById(id, candidate.sourceUrl(), failureReason)
                ));
    }

    private void cacheTeamLogo(Long teamId) {
        loadTeamCandidate(() -> teamRepository.findByTeamId(teamId))
                .ifPresent(candidate -> cache(
                        "team-logo",
                        candidate.externalId(),
                        candidate.sourceUrl(),
                        "api-football/teams/%s".formatted(candidate.externalId()),
                        (objectKey, cachedAt) -> markTeamCached(teamId, candidate.sourceUrl(), objectKey, cachedAt),
                        failureReason -> markTeamFailed(teamId, candidate.sourceUrl(), failureReason)
                ));
    }

    private void cacheTeamLogoByDatabaseId(Long id) {
        loadTeamCandidate(() -> teamRepository.findById(id))
                .ifPresent(candidate -> cache(
                        "team-logo",
                        candidate.externalId(),
                        candidate.sourceUrl(),
                        "api-football/teams/%s".formatted(candidate.externalId()),
                        (objectKey, cachedAt) -> markTeamCachedById(id, candidate.sourceUrl(), objectKey, cachedAt),
                        failureReason -> markTeamFailedById(id, candidate.sourceUrl(), failureReason)
                ));
    }

    private void cacheVenueImage(Long venueId) {
        loadVenueCandidate(() -> venueRepository.findByVenueId(venueId))
                .ifPresent(candidate -> cache(
                        "venue-image",
                        candidate.externalId(),
                        candidate.sourceUrl(),
                        "api-football/venues/%s".formatted(candidate.externalId()),
                        (objectKey, cachedAt) -> markVenueCached(venueId, candidate.sourceUrl(), objectKey, cachedAt),
                        failureReason -> markVenueFailed(venueId, candidate.sourceUrl(), failureReason)
                ));
    }

    private void cacheVenueImageByDatabaseId(Long id) {
        loadVenueCandidate(() -> venueRepository.findById(id))
                .ifPresent(candidate -> cache(
                        "venue-image",
                        candidate.externalId(),
                        candidate.sourceUrl(),
                        "api-football/venues/%s".formatted(candidate.externalId()),
                        (objectKey, cachedAt) -> markVenueCachedById(id, candidate.sourceUrl(), objectKey, cachedAt),
                        failureReason -> markVenueFailedById(id, candidate.sourceUrl(), failureReason)
                ));
    }

    private void cache(String type, Long externalId, String sourceUrl, String objectKeyPrefix, CacheSuccessHandler successHandler,
                       CacheFailureHandler failureHandler) {
        if (!properties.getCache().isEnabled()) {
            return;
        }

        ImageStorageService storageService = imageStorageServiceProvider.getIfAvailable();
        if (storageService == null) {
            failureHandler.handle(FAILURE_STORAGE_NOT_CONFIGURED);
            return;
        }

        try {
            log.debug("Image cache started. type={}, externalId={}, objectKeyPrefix={}",
                    type, externalId, objectKeyPrefix);
            ImageDownloadResult image = imageDownloader.download(sourceUrl);
            String objectKey = objectKeyPrefix + "." + image.extension();
            storageService.upload(objectKey, image.content(), image.contentType());
            successHandler.handle(objectKey, now());
            log.debug("Image cache completed. type={}, externalId={}, objectKey={}, contentType={}, bytes={}",
                    type, externalId, objectKey, image.contentType(), image.content().length);
        } catch (RuntimeException e) {
            failureHandler.handle(failureReason(e));
            log.warn("Image cache failed. type={}, externalId={}, sourceUrl={}", type, externalId, sourceUrl, e);
        }
    }

    private Optional<ImageCandidate> loadPlayerCandidate(Supplier<Optional<Player>> playerLoader) {
        return executeInNewTransaction(() -> playerLoader.get()
                .filter(item -> shouldRequest(item.getPhotoUrl(), item.getPhotoObjectKey(), item.getPhotoCacheFailedAt()))
                .map(item -> {
                    log.debug("Image cache candidate loaded. type=player-photo, externalId={}", item.getPlayerId());
                    return new ImageCandidate(item.getPlayerId(), item.getPhotoUrl());
                }));
    }

    private Optional<ImageCandidate> loadTeamCandidate(Supplier<Optional<Team>> teamLoader) {
        return executeInNewTransaction(() -> teamLoader.get()
                .filter(item -> shouldRequest(item.getLogoUrl(), item.getLogoObjectKey(), item.getLogoCacheFailedAt()))
                .map(item -> {
                    log.debug("Image cache candidate loaded. type=team-logo, externalId={}", item.getTeamId());
                    return new ImageCandidate(item.getTeamId(), item.getLogoUrl());
                }));
    }

    private Optional<ImageCandidate> loadVenueCandidate(Supplier<Optional<Venue>> venueLoader) {
        return executeInNewTransaction(() -> venueLoader.get()
                .filter(item -> shouldRequest(item.getVenueImageUrl(), item.getVenueImageObjectKey(), item.getVenueImageCacheFailedAt()))
                .map(item -> {
                    log.debug("Image cache candidate loaded. type=venue-image, externalId={}", item.getVenueId());
                    return new ImageCandidate(item.getVenueId(), item.getVenueImageUrl());
                }));
    }

    private void markPlayerCached(Long playerId, String sourceUrl, String objectKey, LocalDateTime cachedAt) {
        markSafely("player-photo", playerId, () -> playerRepository.findByPlayerId(playerId)
                .filter(player -> canMark(player.getPhotoUrl(), player.getPhotoObjectKey(), sourceUrl))
                .ifPresent(player -> player.markPhotoCached(objectKey, cachedAt)));
    }

    private void markPlayerCachedById(Long id, String sourceUrl, String objectKey, LocalDateTime cachedAt) {
        markSafely("player-photo", id, () -> playerRepository.findById(id)
                .filter(player -> canMark(player.getPhotoUrl(), player.getPhotoObjectKey(), sourceUrl))
                .ifPresent(player -> player.markPhotoCached(objectKey, cachedAt)));
    }

    private void markPlayerFailed(Long playerId, String sourceUrl, String failureReason) {
        markSafely("player-photo", playerId, () -> playerRepository.findByPlayerId(playerId)
                .filter(player -> canMark(player.getPhotoUrl(), player.getPhotoObjectKey(), sourceUrl))
                .ifPresent(player -> player.markPhotoCacheFailed(now(), failureReason)));
    }

    private void markPlayerFailedById(Long id, String sourceUrl, String failureReason) {
        markSafely("player-photo", id, () -> playerRepository.findById(id)
                .filter(player -> canMark(player.getPhotoUrl(), player.getPhotoObjectKey(), sourceUrl))
                .ifPresent(player -> player.markPhotoCacheFailed(now(), failureReason)));
    }

    private void markTeamCached(Long teamId, String sourceUrl, String objectKey, LocalDateTime cachedAt) {
        markSafely("team-logo", teamId, () -> teamRepository.findByTeamId(teamId)
                .filter(team -> canMark(team.getLogoUrl(), team.getLogoObjectKey(), sourceUrl))
                .ifPresent(team -> team.markLogoCached(objectKey, cachedAt)));
    }

    private void markTeamCachedById(Long id, String sourceUrl, String objectKey, LocalDateTime cachedAt) {
        markSafely("team-logo", id, () -> teamRepository.findById(id)
                .filter(team -> canMark(team.getLogoUrl(), team.getLogoObjectKey(), sourceUrl))
                .ifPresent(team -> team.markLogoCached(objectKey, cachedAt)));
    }

    private void markTeamFailed(Long teamId, String sourceUrl, String failureReason) {
        markSafely("team-logo", teamId, () -> teamRepository.findByTeamId(teamId)
                .filter(team -> canMark(team.getLogoUrl(), team.getLogoObjectKey(), sourceUrl))
                .ifPresent(team -> team.markLogoCacheFailed(now(), failureReason)));
    }

    private void markTeamFailedById(Long id, String sourceUrl, String failureReason) {
        markSafely("team-logo", id, () -> teamRepository.findById(id)
                .filter(team -> canMark(team.getLogoUrl(), team.getLogoObjectKey(), sourceUrl))
                .ifPresent(team -> team.markLogoCacheFailed(now(), failureReason)));
    }

    private void markVenueCached(Long venueId, String sourceUrl, String objectKey, LocalDateTime cachedAt) {
        markSafely("venue-image", venueId, () -> venueRepository.findByVenueId(venueId)
                .filter(venue -> canMark(venue.getVenueImageUrl(), venue.getVenueImageObjectKey(), sourceUrl))
                .ifPresent(venue -> venue.markVenueImageCached(objectKey, cachedAt)));
    }

    private void markVenueCachedById(Long id, String sourceUrl, String objectKey, LocalDateTime cachedAt) {
        markSafely("venue-image", id, () -> venueRepository.findById(id)
                .filter(venue -> canMark(venue.getVenueImageUrl(), venue.getVenueImageObjectKey(), sourceUrl))
                .ifPresent(venue -> venue.markVenueImageCached(objectKey, cachedAt)));
    }

    private void markVenueFailed(Long venueId, String sourceUrl, String failureReason) {
        markSafely("venue-image", venueId, () -> venueRepository.findByVenueId(venueId)
                .filter(venue -> canMark(venue.getVenueImageUrl(), venue.getVenueImageObjectKey(), sourceUrl))
                .ifPresent(venue -> venue.markVenueImageCacheFailed(now(), failureReason)));
    }

    private void markVenueFailedById(Long id, String sourceUrl, String failureReason) {
        markSafely("venue-image", id, () -> venueRepository.findById(id)
                .filter(venue -> canMark(venue.getVenueImageUrl(), venue.getVenueImageObjectKey(), sourceUrl))
                .ifPresent(venue -> venue.markVenueImageCacheFailed(now(), failureReason)));
    }

    private boolean shouldRequest(String sourceUrl, String objectKey, LocalDateTime failedAt) {
        if (!properties.getCache().isEnabled() || sourceUrl == null || sourceUrl.isBlank()
                || objectKey != null && !objectKey.isBlank()) {
            return false;
        }
        return failedAt == null || failedAt.isBefore(now().minus(properties.getCache().getFailureCooldown()));
    }

    private boolean canMark(String currentSourceUrl, String objectKey, String expectedSourceUrl) {
        return Objects.equals(currentSourceUrl, expectedSourceUrl) && (objectKey == null || objectKey.isBlank());
    }

    private <T> T executeInNewTransaction(Supplier<T> task) {
        TransactionTemplate template = newTransactionTemplate();
        return template.execute(status -> task.get());
    }

    private void executeInNewTransactionWithoutResult(Runnable task) {
        TransactionTemplate template = newTransactionTemplate();
        template.executeWithoutResult(status -> task.run());
    }

    private void markSafely(String type, Long externalId, Runnable task) {
        try {
            executeInNewTransactionWithoutResult(task);
        } catch (RuntimeException e) {
            log.error("Image cache metadata update failed. type={}, externalId={}", type, externalId, e);
        }
    }

    private TransactionTemplate newTransactionTemplate() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private void afterCommit(Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }

    private String failureReason(RuntimeException e) {
        if (e instanceof ImageDownloadException) {
            return FAILURE_DOWNLOAD_FAILED;
        }
        return FAILURE_CACHE_FAILED;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private record ImageCandidate(Long externalId, String sourceUrl) {
    }

    @FunctionalInterface
    private interface CacheSuccessHandler {
        void handle(String objectKey, LocalDateTime cachedAt);
    }

    @FunctionalInterface
    private interface CacheFailureHandler {
        void handle(String failureReason);
    }
}
