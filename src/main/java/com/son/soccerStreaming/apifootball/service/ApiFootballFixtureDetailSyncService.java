package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.apifootball.client.ApiFootballClient;
import com.son.soccerStreaming.apifootball.dto.ApiFootballLiveDto;
import com.son.soccerStreaming.fixture.dto.FixtureEventDto;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import com.son.soccerStreaming.player.event.PlayerSeasonStatRebuildRequested;
import com.son.soccerStreaming.player.service.PlayerTeamSeasonStatAggregationService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StopWatch;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiFootballFixtureDetailSyncService {

    private final ApiFootballClient apiFootballClient;
    private final ApiFootballFixtureSyncService apiFootballFixtureSyncService;
    private final ApiFootballFixtureEventSyncService apiFootballFixtureEventSyncService;
    private final ApiFootballFixtureLineupSyncService apiFootballFixtureLineupSyncService;
    private final ApiFootballFixtureStatSyncService apiFootballFixtureStatSyncService;
    private final ApiFootballFixturePlayerStatSyncService apiFootballFixturePlayerStatSyncService;
    private final PlayerTeamSeasonStatAggregationService playerTeamSeasonStatAggregationService;
    private final ApplicationEventPublisher eventPublisher;
    private final FixtureRepository fixtureRepository;
    private final OptimisticLockRetryExecutor optimisticLockRetryExecutor;
    private final EntityManager entityManager;
    private final ApiFootballSyncStatusService apiFootballSyncStatusService;
    private final FixtureDetailGenerationStore generationStore;
    private final FixtureDetailGenerationEventWriter generationEventWriter;
    private final FixtureDetailGenerationAggregationService generationAggregationService;
    private final FixtureDetailGenerationPublisher generationPublisher;
    private final FixtureDetailGenerationResolver generationResolver;
    private final FixtureDetailGenerationReadStore generationReadStore;
    private final FixtureDetailLiveChangeStore liveChangeStore;

    @Value("${api-football.sync.fixture-details.chunk-size:10}")
    private int chunkSize;

    @Value("${fixture-detail.generation-batch.enabled:false}")
    private boolean generationBatchEnabled;

    public FixtureDetailSyncResult syncFixtureDetail(Long fixtureId, boolean applyLiveStandingImpact) {
        apiFootballSyncStatusService.recordAttempt("fixture-detail", "Fixture Detail");
        FixtureDetailSyncResult result = apiFootballClient.getFixture(fixtureId).stream()
                .findFirst()
                .map(response -> optimisticLockRetryExecutor.execute(
                        "fixture-detail:fixture=%s".formatted(fixtureId),
                        () -> syncFixtureDetail(response, applyLiveStandingImpact, true)
                ))
                .orElseGet(() -> FixtureDetailSyncResult.empty(fixtureId));
        if (result.fixtureId() != null) {
            apiFootballSyncStatusService.recordSuccess("fixture-detail", "Fixture Detail");
        }
        return result;
    }

    @Transactional
    public FixtureDetailSyncResult syncFixtureDetail(ApiFootballLiveDto.FixtureResponse response,
                                                     boolean applyLiveStandingImpact) {
        return syncFixtureDetail(response, applyLiveStandingImpact, true);
    }

    private FixtureDetailSyncResult syncFixtureDetail(
            ApiFootballLiveDto.FixtureResponse response,
            boolean applyLiveStandingImpact,
            boolean rebuildSeasonStats
    ) {
        ApiFootballSyncExecutionGuard.checkCurrentLease();
        StopWatch stopWatch = new StopWatch("fixture-detail-" + fixtureIdOf(response));
        stopWatch.start("fixture");
        Optional<Fixture> fixture = apiFootballFixtureSyncService.syncFixtureResponse(response, applyLiveStandingImpact);
        stopWatch.stop();
        if (fixture.isEmpty()) {
            Long fixtureId = response.getFixture() != null ? response.getFixture().getId() : null;
            return FixtureDetailSyncResult.empty(fixtureId);
        }

        ApiFootballSyncExecutionGuard.checkCurrentLease();
        stopWatch.start("events");
        FixtureEventDto latestEvent = apiFootballFixtureEventSyncService.syncEvents(fixture.get(), response.getEvents());
        stopWatch.stop();

        ApiFootballSyncExecutionGuard.checkCurrentLease();
        stopWatch.start("lineups");
        int lineups = apiFootballFixtureLineupSyncService.syncLineups(fixture.get(), response.getLineups());
        stopWatch.stop();

        ApiFootballSyncExecutionGuard.checkCurrentLease();
        stopWatch.start("teamStats");
        int teamStats = apiFootballFixtureStatSyncService.syncFixtureStats(fixture.get(), response.getStatistics());
        stopWatch.stop();

        ApiFootballSyncExecutionGuard.checkCurrentLease();
        stopWatch.start("playerStats");
        int playerStats = apiFootballFixturePlayerStatSyncService.syncPlayerStats(fixture.get(), response.getPlayers());
        stopWatch.stop();

        if (rebuildSeasonStats) {
            eventPublisher.publishEvent(new PlayerSeasonStatRebuildRequested(
                    39,
                    fixture.get().getFixtureId(),
                    fixture.get().getSeason()
            ));
        }

        log.debug("API-Football fixture detail processed. fixtureId={}, totalMs={}, {}",
                fixture.get().getFixtureId(),
                stopWatch.getTotalTimeMillis(),
                shortSummary(stopWatch));

        return FixtureDetailSyncResult.builder()
                .fixtureId(fixture.get().getFixtureId())
                .latestEvent(latestEvent)
                .lineupCount(lineups)
                .teamStatCount(teamStats)
                .playerStatCount(playerStats)
                .build();
    }

    public int syncFixtureDetails(List<Fixture> fixtures, boolean applyLiveStandingImpact) {
        return syncFixtureDetailsWithResults(fixtures, applyLiveStandingImpact).size();
    }

    public List<FixtureDetailSyncResult> syncFixtureDetailsWithResults(List<Fixture> fixtures, boolean applyLiveStandingImpact) {
        List<Long> fixtureIds = fixtures.stream()
                .map(Fixture::getFixtureId)
                .toList();
        return syncFixtureDetailsByIdsWithResults(fixtureIds, applyLiveStandingImpact, true, SyncProgressReporter.NO_OP);
    }

    public int syncFixtureDetailsByIds(List<Long> fixtureIds, boolean applyLiveStandingImpact) {
        return syncFixtureDetailsByIdsWithResults(fixtureIds, applyLiveStandingImpact).size();
    }

    public List<FixtureDetailSyncResult> syncFixtureDetailsByIdsWithResults(List<Long> fixtureIds, boolean applyLiveStandingImpact) {
        return syncFixtureDetailsByIdsWithResults(fixtureIds, applyLiveStandingImpact, true, SyncProgressReporter.NO_OP);
    }

    private List<FixtureDetailSyncResult> syncFixtureDetailsByIdsWithResults(
            List<Long> fixtureIds,
            boolean applyLiveStandingImpact,
            boolean rebuildAffectedSeasonStats,
            SyncProgressReporter progressReporter
    ) {
        long startedAtNanos = System.nanoTime();
        List<FixtureDetailSyncResult> results = new ArrayList<>();
        List<List<Long>> chunks = chunks(fixtureIds);
        List<List<Long>> failedChunks = new ArrayList<>();
        Exception firstFailure = null;
        int processedUnits = 0;
        int successfulUnits = 0;
        int failedUnits = 0;
        progressReporter.beginPhase("SYNCING_FIXTURES", fixtureIds.size(), "fixtures", 0);
        for (int i = 0; i < chunks.size(); i++) {
            progressReporter.checkCancelled();
            List<Long> chunk = chunks.get(i);
            StopWatch chunkWatch = new StopWatch("fixture-detail-chunk-" + (i + 1));
            try {
                log.debug("API-Football fixture detail chunk started. chunk={}/{}, size={}, fixtureIds={}",
                        i + 1, chunks.size(), chunk.size(), chunk);

                chunkWatch.start("api");
                List<ApiFootballLiveDto.FixtureResponse> responses = apiFootballClient.getFixturesByIds(chunk);
                chunkWatch.stop();
                validateFixtureResponses(chunk, responses);
                progressReporter.checkCancelled();

                chunkWatch.start("upsert");
                List<FixtureDetailSyncResult> chunkResults = optimisticLockRetryExecutor.execute(
                        "fixture-detail:fixtures=%s".formatted(chunk),
                        () -> {
                            List<FixtureDetailSyncResult> processed = new ArrayList<>();
                            for (ApiFootballLiveDto.FixtureResponse response : responses) {
                                progressReporter.checkCancelled();
                                FixtureDetailSyncResult result = syncFixtureDetail(
                                        response, applyLiveStandingImpact, false);
                                if (result.fixtureId() != null) {
                                    processed.add(result);
                                }
                            }
                            if (processed.size() != chunk.size()) {
                                throw new IllegalStateException("Fixture detail chunk could not persist every requested fixture");
                            }
                            // Admin-triggered bulk sync runs in a web request, so clear managed entities per chunk.
                            entityManager.flush();
                            entityManager.clear();
                            return processed;
                        }
                );
                results.addAll(chunkResults);
                processedUnits += chunk.size();
                successfulUnits += chunkResults.size();
                progressReporter.update(processedUnits, successfulUnits, failedUnits, results.size());
                chunkWatch.stop();

                if (rebuildAffectedSeasonStats) {
                    for (FixtureDetailSyncResult result : chunkResults) {
                        fixtureRepository.findByFixtureId(result.fixtureId()).ifPresent(fixture ->
                                playerTeamSeasonStatAggregationService.rebuildForFixture(
                                        39,
                                        fixture.getFixtureId(),
                                        fixture.getSeason()
                                ));
                    }
                }

                log.info("API-Football fixture detail chunk completed. chunk={}/{}, responseCount={}, totalMs={}, {}",
                        i + 1, chunks.size(), responses.size(), chunkWatch.getTotalTimeMillis(), shortSummary(chunkWatch));
            } catch (SyncCancelledException | SyncLockLostException exception) {
                throw exception;
            } catch (Exception e) {
                if (firstFailure == null) {
                    firstFailure = e;
                }
                if (chunkWatch.isRunning()) {
                    chunkWatch.stop();
                }
                failedChunks.add(List.copyOf(chunk));
                processedUnits += chunk.size();
                failedUnits += chunk.size();
                progressReporter.error("FIXTURE_CHUNK", chunk.toString(), e.getMessage());
                progressReporter.update(processedUnits, successfulUnits, failedUnits, results.size());
                log.error("API-Football fixture detail chunk sync failed. fixtureIds={}", chunk, e);
            }
        }
        if (!failedChunks.isEmpty()) {
            throw new ApiFootballFixtureDetailSyncException(failedChunks, chunks.size(), firstFailure);
        }
        long totalMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
        log.info("API-Football fixture detail sync completed. requestedCount={}, processedCount={}, "
                        + "chunkCount={}, failedChunkCount=0, totalMs={}",
                fixtureIds.size(), results.size(), chunks.size(), totalMs);
        return results;
    }

    public int syncSeasonFixtureDetails(Integer season, boolean applyLiveStandingImpact) {
        return syncSeasonFixtureDetails(season, applyLiveStandingImpact, SyncProgressReporter.NO_OP);
    }

    /** Fetches one season into a private generation. Normalization and publication happen separately. */
    public long stageSeasonFixtureResponses(Integer season, SyncProgressReporter progressReporter) {
        List<Long> fixtureIds = fixtureRepository.findAllBySeasonOrderByFixtureDateAsc(season).stream()
                .map(Fixture::getFixtureId)
                .toList();
        long generationId = generationStore.create(season, fixtureIds);
        int processed = 0;
        int successful = 0;
        progressReporter.beginPhase("STAGING_FIXTURES", fixtureIds.size(), "fixtures", 0);
        List<List<Long>> seasonChunks = chunks(fixtureIds);
        try {
            for (List<Long> chunk : seasonChunks) {
                progressReporter.checkCancelled();
                try {
                    List<ApiFootballLiveDto.FixtureResponse> responses = apiFootballClient.getFixturesByIds(chunk);
                    validateFixtureResponses(chunk, responses);
                    progressReporter.checkCancelled();
                    generationStore.stageChunk(generationId, chunk, responses);
                    for (ApiFootballLiveDto.FixtureResponse response : responses) {
                        Long fixtureId = fixtureIdOf(response);
                        Fixture current = publishedFixtureForPreview(season, fixtureId);
                        Fixture draft = apiFootballFixtureSyncService.previewFixtureResponse(current, response);
                        var lineupDrafts = apiFootballFixtureLineupSyncService.previewLineups(
                                current, draft, response.getLineups());
                        generationStore.applyFixtureResponse(generationId, draft);
                        generationEventWriter.apply(generationId, current, response.getEvents());
                        generationStore.applyLineups(generationId, current.getId(), lineupDrafts);
                        generationStore.applyTeamStats(generationId, current.getId(),
                                apiFootballFixtureStatSyncService.previewFixtureStats(current, response.getStatistics()));
                        generationStore.applyPlayerStats(generationId, current.getId(),
                                apiFootballFixturePlayerStatSyncService.previewPlayerStats(current, response.getPlayers()));
                        generationStore.markFixtureMaterialized(generationId, fixtureId);
                    }
                    successful += chunk.size();
                } catch (SyncCancelledException | SyncLockLostException exception) {
                    throw exception;
                } catch (Exception exception) {
                    progressReporter.error("FIXTURE_CHUNK", chunk.toString(), exception.getMessage());
                    processed += chunk.size();
                    progressReporter.update(processed, successful, processed - successful, successful);
                    throw new ApiFootballFixtureDetailSyncException(
                            List.of(List.copyOf(chunk)), seasonChunks.size(), exception);
                }
                processed += chunk.size();
                progressReporter.update(processed, successful, processed - successful, successful);
            }
            progressReporter.checkCancelled();
            generationStore.markNormalized(generationId);
            progressReporter.beginPhase("REBUILDING_SEASON_STATS", 1, "season", successful);
            generationAggregationService.rebuild(generationId, season);
            generationStore.markAggregated(generationId);
            progressReporter.update(1, 1, 0, successful);
            return generationId;
        } catch (RuntimeException exception) {
            try {
                generationStore.markFailed(generationId);
            } catch (RuntimeException statusFailure) {
                exception.addSuppressed(statusFailure);
            }
            throw exception;
        }
    }

    private Fixture publishedFixtureForPreview(int season, long fixtureId) {
        Long activeGenerationId = generationStore.activeGenerationId(season);
        if (activeGenerationId != null && activeGenerationId > 0) {
            return generationReadStore.findFixture(activeGenerationId, fixtureId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Published fixture disappeared during generation preparation: " + fixtureId));
        }
        return fixtureRepository.findByFixtureId(fixtureId)
                .orElseThrow(() -> new IllegalStateException(
                        "Published fixture disappeared during generation preparation: " + fixtureId));
    }

    public int syncSeasonFixtureDetails(Integer season, boolean applyLiveStandingImpact,
                                        SyncProgressReporter progressReporter) {
        return syncSeasonFixtureDetails(season, applyLiveStandingImpact, progressReporter, null);
    }

    public int syncSeasonFixtureDetails(Integer season, boolean applyLiveStandingImpact,
                                        SyncProgressReporter progressReporter, String jobId) {
        apiFootballSyncStatusService.recordAttempt("fixture-details", "Season Details", season);
        if (generationBatchEnabled) {
            if (!generationResolver.enabled()) {
                throw new IllegalStateException("Generation reads must be enabled before generation batch writes");
            }
            Integer publishedCount = generationStore.publishedJobFixtureCount(jobId);
            if (publishedCount != null) {
                apiFootballSyncStatusService.recordSuccess("fixture-details", "Season Details", season);
                return publishedCount;
            }
            int fixtureCount = fixtureRepository.findAllBySeasonOrderByFixtureDateAsc(season).size();
            long generationId = stageSeasonFixtureResponses(season, progressReporter);
            try {
                progressReporter.checkCancelled();
                generationPublisher.publish(season, generationId, jobId, fixtureCount);
            } catch (RuntimeException exception) {
                try {
                    generationStore.markFailed(generationId);
                } catch (RuntimeException statusFailure) {
                    exception.addSuppressed(statusFailure);
                }
                throw exception;
            }
            apiFootballSyncStatusService.recordSuccess("fixture-details", "Season Details", season);
            return fixtureCount;
        }
        List<Long> fixtureIds = fixtureRepository.findAllBySeasonOrderByFixtureDateAsc(season).stream()
                .map(Fixture::getFixtureId)
                .toList();
        int syncedCount = syncFixtureDetailsByIdsWithResults(
                fixtureIds, applyLiveStandingImpact, false, progressReporter).size();
        progressReporter.checkCancelled();
        progressReporter.beginPhase("REBUILDING_SEASON_STATS", 0, "season", syncedCount);
        playerTeamSeasonStatAggregationService.rebuildSeason(39, season);
        progressReporter.beginPhase("REBUILDING_SEASON_STATS", 1, "season", syncedCount);
        progressReporter.update(1, 1, 0, syncedCount);
        progressReporter.checkCancelled();
        apiFootballSyncStatusService.recordSuccess("fixture-details", "Season Details", season);
        return syncedCount;
    }

    private List<List<Long>> chunks(List<Long> fixtureIds) {
        List<List<Long>> chunks = new ArrayList<>();
        int size = Math.max(1, chunkSize);
        for (int i = 0; i < fixtureIds.size(); i += size) {
            chunks.add(fixtureIds.subList(i, Math.min(i + size, fixtureIds.size())));
        }
        return chunks;
    }

    private void validateFixtureResponses(List<Long> requestedIds, List<ApiFootballLiveDto.FixtureResponse> responses) {
        if (responses == null) {
            throw new IllegalStateException("Fixture detail API returned no response list");
        }
        Set<Long> requested = new HashSet<>(requestedIds);
        Set<Long> received = new HashSet<>();
        for (ApiFootballLiveDto.FixtureResponse response : responses) {
            Long fixtureId = fixtureIdOf(response);
            if (fixtureId == null || !requested.contains(fixtureId) || !received.add(fixtureId)) {
                throw new IllegalStateException("Fixture detail API returned an invalid or duplicate fixture ID: " + fixtureId);
            }
        }
        if (!received.containsAll(requested)) {
            throw new IllegalStateException("Fixture detail API omitted requested fixture IDs: " + requestedIds.stream()
                    .filter(id -> !received.contains(id)).toList());
        }
    }

    private Long fixtureIdOf(ApiFootballLiveDto.FixtureResponse response) {
        return response != null && response.getFixture() != null ? response.getFixture().getId() : null;
    }

    private String shortSummary(StopWatch stopWatch) {
        StringBuilder builder = new StringBuilder("tasks=[");
        StopWatch.TaskInfo[] taskInfo = stopWatch.getTaskInfo();
        for (int i = 0; i < taskInfo.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(taskInfo[i].getTaskName())
                    .append("=")
                    .append(taskInfo[i].getTimeMillis())
                    .append("ms");
        }
        return builder.append("]").toString();
    }

    public record FixtureDetailSyncResult(
            Long fixtureId,
            FixtureEventDto latestEvent,
            int lineupCount,
            int teamStatCount,
            int playerStatCount
    ) {
        static FixtureDetailSyncResult empty(Long fixtureId) {
            return new FixtureDetailSyncResult(fixtureId, null, 0, 0, 0);
        }

        static Builder builder() {
            return new Builder();
        }

        static class Builder {
            private Long fixtureId;
            private FixtureEventDto latestEvent;
            private int lineupCount;
            private int teamStatCount;
            private int playerStatCount;

            Builder fixtureId(Long fixtureId) {
                this.fixtureId = fixtureId;
                return this;
            }

            Builder latestEvent(FixtureEventDto latestEvent) {
                this.latestEvent = latestEvent;
                return this;
            }

            Builder lineupCount(int lineupCount) {
                this.lineupCount = lineupCount;
                return this;
            }

            Builder teamStatCount(int teamStatCount) {
                this.teamStatCount = teamStatCount;
                return this;
            }

            Builder playerStatCount(int playerStatCount) {
                this.playerStatCount = playerStatCount;
                return this;
            }

            FixtureDetailSyncResult build() {
                return new FixtureDetailSyncResult(fixtureId, latestEvent, lineupCount, teamStatCount, playerStatCount);
            }
        }
    }
}
