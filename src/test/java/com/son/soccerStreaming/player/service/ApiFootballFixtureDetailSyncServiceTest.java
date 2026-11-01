package com.son.soccerStreaming.player.service;

import com.son.soccerStreaming.apifootball.client.ApiFootballClient;
import com.son.soccerStreaming.apifootball.dto.ApiFootballLiveDto;
import com.son.soccerStreaming.apifootball.service.ApiFootballFixtureDetailSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballFixtureDetailSyncException;
import com.son.soccerStreaming.apifootball.service.ApiFootballFixtureEventSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballFixtureLineupSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballFixturePlayerStatSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballFixtureStatSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballFixtureSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballSyncStatusService;
import com.son.soccerStreaming.apifootball.service.OptimisticLockRetryExecutor;
import com.son.soccerStreaming.apifootball.service.FixtureDetailGenerationStore;
import com.son.soccerStreaming.apifootball.service.FixtureDetailGenerationEventWriter;
import com.son.soccerStreaming.apifootball.service.FixtureDetailGenerationAggregationService;
import com.son.soccerStreaming.apifootball.service.FixtureDetailGenerationPublisher;
import com.son.soccerStreaming.apifootball.service.FixtureDetailGenerationResolver;
import com.son.soccerStreaming.apifootball.service.FixtureDetailGenerationReadStore;
import com.son.soccerStreaming.apifootball.service.FixtureDetailLiveChangeStore;
import com.son.soccerStreaming.apifootball.service.SyncProgressReporter;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import com.son.soccerStreaming.player.event.PlayerSeasonStatRebuildRequested;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class ApiFootballFixtureDetailSyncServiceTest {

    @Mock private ApiFootballClient apiFootballClient;
    @Mock private ApiFootballFixtureSyncService fixtureSyncService;
    @Mock private ApiFootballFixtureEventSyncService fixtureEventSyncService;
    @Mock private ApiFootballFixtureLineupSyncService fixtureLineupSyncService;
    @Mock private ApiFootballFixtureStatSyncService fixtureStatSyncService;
    @Mock private ApiFootballFixturePlayerStatSyncService fixturePlayerStatSyncService;
    @Mock private PlayerTeamSeasonStatAggregationService aggregationService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private FixtureRepository fixtureRepository;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private EntityManager entityManager;
    @Mock private ApiFootballSyncStatusService syncStatusService;
    @Mock private OptimisticLockRetryExecutor optimisticLockRetryExecutor;
    @Mock private FixtureDetailGenerationStore generationStore;
    @Mock private FixtureDetailGenerationEventWriter generationEventWriter;
    @Mock private FixtureDetailGenerationAggregationService generationAggregationService;
    @Mock private FixtureDetailGenerationPublisher generationPublisher;
    @Mock private FixtureDetailGenerationResolver generationResolver;
    @Mock private FixtureDetailGenerationReadStore generationReadStore;
    @Mock private FixtureDetailLiveChangeStore liveChangeStore;

    @InjectMocks
    private ApiFootballFixtureDetailSyncService service;

    @Test
    void generationBatchPublishesOnlyAfterPrivateSeasonIsComplete() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "generationBatchEnabled", true);
        when(generationResolver.enabled()).thenReturn(true);
        when(generationStore.publishedJobFixtureCount("job-1")).thenReturn(null);
        when(fixtureRepository.findAllBySeasonOrderByFixtureDateAsc(2025))
                .thenReturn(java.util.List.of());
        when(generationStore.create(2025, java.util.List.of())).thenReturn(7L);

        service.syncSeasonFixtureDetails(2025, false, SyncProgressReporter.NO_OP, "job-1");

        var ordered = inOrder(generationStore, generationAggregationService, generationPublisher);
        ordered.verify(generationStore).markNormalized(7L);
        ordered.verify(generationAggregationService).rebuild(7L, 2025);
        ordered.verify(generationStore).markAggregated(7L);
        ordered.verify(generationPublisher).publish(2025, 7L, "job-1", 0);
        verify(aggregationService, never()).rebuildSeason(any(), any());
    }

    @Test
    void repeatedRedisDeliveryUsesPublishedJobReceipt() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "generationBatchEnabled", true);
        when(generationResolver.enabled()).thenReturn(true);
        when(generationStore.publishedJobFixtureCount("job-1")).thenReturn(50);

        org.assertj.core.api.Assertions.assertThat(service.syncSeasonFixtureDetails(
                2025, false, SyncProgressReporter.NO_OP, "job-1")).isEqualTo(50);
        verify(generationStore, never()).create(org.mockito.ArgumentMatchers.anyInt(), any());
        verify(generationPublisher, never()).publish(
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyLong(),
                any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void publishesSeasonStatRebuildInsteadOfRunningItInsideFixtureTransaction() {
        ApiFootballLiveDto.FixtureResponse response = new ApiFootballLiveDto.FixtureResponse();
        Fixture fixture = Fixture.builder().fixtureId(100L).season(2025).build();
        when(fixtureSyncService.syncFixtureResponse(response, false)).thenReturn(Optional.of(fixture));

        service.syncFixtureDetail(response, false);

        verify(eventPublisher).publishEvent(new PlayerSeasonStatRebuildRequested(39, 100L, 2025));
        verify(aggregationService, never()).rebuildForFixture(any(), any(), any());
    }

    @Test
    void reportsSeasonRebuildAsSeparatePhase() {
        SyncProgressReporter reporter = org.mockito.Mockito.mock(SyncProgressReporter.class);
        when(fixtureRepository.findAllBySeasonOrderByFixtureDateAsc(2025)).thenReturn(java.util.List.of());

        service.syncSeasonFixtureDetails(2025, false, reporter);

        var ordered = inOrder(reporter, aggregationService);
        ordered.verify(reporter).beginPhase("SYNCING_FIXTURES", 0, "fixtures", 0);
        ordered.verify(reporter).checkCancelled();
        ordered.verify(reporter).beginPhase("REBUILDING_SEASON_STATS", 0, "season", 0);
        ordered.verify(aggregationService).rebuildSeason(39, 2025);
        ordered.verify(reporter).beginPhase("REBUILDING_SEASON_STATS", 1, "season", 0);
        ordered.verify(reporter).update(1, 1, 0, 0);
    }

    @Test
    void missingFixtureResponseFailsSeasonSyncBeforeAggregateRebuild() {
        Fixture first = Fixture.builder().fixtureId(100L).season(2025).build();
        Fixture second = Fixture.builder().fixtureId(101L).season(2025).build();
        ApiFootballLiveDto.FixtureResponse response = new ApiFootballLiveDto.FixtureResponse();
        ApiFootballLiveDto.FixtureInfo fixtureInfo = new ApiFootballLiveDto.FixtureInfo();
        org.springframework.test.util.ReflectionTestUtils.setField(fixtureInfo, "id", 100L);
        org.springframework.test.util.ReflectionTestUtils.setField(response, "fixture", fixtureInfo);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "chunkSize", 10);
        when(fixtureRepository.findAllBySeasonOrderByFixtureDateAsc(2025))
                .thenReturn(java.util.List.of(first, second));
        when(apiFootballClient.getFixturesByIds(java.util.List.of(100L, 101L)))
                .thenReturn(java.util.List.of(response));
        assertThatThrownBy(() -> service.syncSeasonFixtureDetails(2025, false))
                .isInstanceOfSatisfying(ApiFootballFixtureDetailSyncException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getFailedChunks())
                                .containsExactly(java.util.List.of(100L, 101L)));
        verify(optimisticLockRetryExecutor, never()).execute(any(), any());
        verify(aggregationService, never()).rebuildSeason(any(), any());
    }

    @Test
    void failedStagingLeavesPublishedFixtureRowsUntouched() {
        Fixture first = Fixture.builder().fixtureId(100L).season(2025).build();
        Fixture second = Fixture.builder().fixtureId(101L).season(2025).build();
        ApiFootballLiveDto.FixtureResponse response = new ApiFootballLiveDto.FixtureResponse();
        ApiFootballLiveDto.FixtureInfo fixtureInfo = new ApiFootballLiveDto.FixtureInfo();
        org.springframework.test.util.ReflectionTestUtils.setField(fixtureInfo, "id", 100L);
        org.springframework.test.util.ReflectionTestUtils.setField(response, "fixture", fixtureInfo);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "chunkSize", 10);
        when(fixtureRepository.findAllBySeasonOrderByFixtureDateAsc(2025))
                .thenReturn(java.util.List.of(first, second));
        when(generationStore.create(2025, java.util.List.of(100L, 101L))).thenReturn(7L);
        when(apiFootballClient.getFixturesByIds(java.util.List.of(100L, 101L)))
                .thenReturn(java.util.List.of(response));

        assertThatThrownBy(() -> service.stageSeasonFixtureResponses(2025, SyncProgressReporter.NO_OP))
                .isInstanceOf(ApiFootballFixtureDetailSyncException.class);

        verify(generationStore).markFailed(7L);
        verify(generationStore, never()).stageChunk(org.mockito.ArgumentMatchers.anyLong(), any(), any());
        verify(optimisticLockRetryExecutor, never()).execute(any(), any());
        verify(aggregationService, never()).rebuildSeason(any(), any());
    }

    @Test
    void privateSeasonStagingStopsAfterTheFirstFailedChunk() {
        Fixture first = Fixture.builder().fixtureId(100L).season(2025).build();
        Fixture second = Fixture.builder().fixtureId(101L).season(2025).build();
        Fixture third = Fixture.builder().fixtureId(102L).season(2025).build();
        ApiFootballLiveDto.FixtureResponse response = new ApiFootballLiveDto.FixtureResponse();
        ApiFootballLiveDto.FixtureInfo fixtureInfo = new ApiFootballLiveDto.FixtureInfo();
        org.springframework.test.util.ReflectionTestUtils.setField(fixtureInfo, "id", 100L);
        org.springframework.test.util.ReflectionTestUtils.setField(response, "fixture", fixtureInfo);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "chunkSize", 1);
        when(fixtureRepository.findAllBySeasonOrderByFixtureDateAsc(2025))
                .thenReturn(java.util.List.of(first, second, third));
        when(generationStore.create(2025, java.util.List.of(100L, 101L, 102L))).thenReturn(7L);
        when(apiFootballClient.getFixturesByIds(java.util.List.of(100L)))
                .thenReturn(java.util.List.of(response));
        when(apiFootballClient.getFixturesByIds(java.util.List.of(101L)))
                .thenThrow(new IllegalStateException("API unavailable"));
        when(fixtureRepository.findByFixtureId(100L)).thenReturn(Optional.of(first));
        when(fixtureSyncService.previewFixtureResponse(first, response)).thenReturn(first);

        assertThatThrownBy(() -> service.stageSeasonFixtureResponses(2025, SyncProgressReporter.NO_OP))
                .isInstanceOfSatisfying(ApiFootballFixtureDetailSyncException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getFailedChunks())
                                .containsExactly(java.util.List.of(101L)));

        verify(generationStore).stageChunk(7L, java.util.List.of(100L), java.util.List.of(response));
        verify(apiFootballClient, never()).getFixturesByIds(java.util.List.of(102L));
        verify(generationStore).markFailed(7L);
        verify(generationStore, never()).markNormalized(7L);
        verify(generationAggregationService, never()).rebuild(7L, 2025);
        verify(generationPublisher, never()).publish(
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyLong(),
                any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void successfulStagingWritesOnlyToPrivateGeneration() {
        Fixture fixture = Fixture.builder().fixtureId(100L).season(2025).build();
        ApiFootballLiveDto.FixtureResponse response = new ApiFootballLiveDto.FixtureResponse();
        ApiFootballLiveDto.FixtureInfo fixtureInfo = new ApiFootballLiveDto.FixtureInfo();
        org.springframework.test.util.ReflectionTestUtils.setField(fixtureInfo, "id", 100L);
        org.springframework.test.util.ReflectionTestUtils.setField(response, "fixture", fixtureInfo);
        when(fixtureRepository.findAllBySeasonOrderByFixtureDateAsc(2025))
                .thenReturn(java.util.List.of(fixture));
        when(generationStore.create(2025, java.util.List.of(100L))).thenReturn(7L);
        when(apiFootballClient.getFixturesByIds(java.util.List.of(100L)))
                .thenReturn(java.util.List.of(response));
        when(fixtureRepository.findByFixtureId(100L)).thenReturn(Optional.of(fixture));
        when(fixtureSyncService.previewFixtureResponse(fixture, response)).thenReturn(fixture);

        org.assertj.core.api.Assertions.assertThat(
                service.stageSeasonFixtureResponses(2025, SyncProgressReporter.NO_OP)).isEqualTo(7L);

        verify(generationStore).stageChunk(7L, java.util.List.of(100L), java.util.List.of(response));
        verify(generationStore).applyFixtureResponse(7L, fixture);
        verify(generationEventWriter).apply(7L, fixture, null);
        verify(generationStore).markFixtureMaterialized(7L, 100L);
        verify(generationStore).markNormalized(7L);
        verify(generationAggregationService).rebuild(7L, 2025);
        verify(generationStore).markAggregated(7L);
        verify(generationStore, never()).markFailed(org.mockito.ArgumentMatchers.anyLong());
        verify(optimisticLockRetryExecutor, never()).execute(any(), any());
        verify(aggregationService, never()).rebuildSeason(any(), any());
    }

    @Test
    void nextSeasonBatchPreviewsFromCurrentPublishedGeneration() {
        Fixture base = Fixture.builder().id(10L).fixtureId(100L).season(2025).build();
        Fixture published = Fixture.builder().id(10L).fixtureId(100L).season(2025)
                .homeScore(3).build();
        ApiFootballLiveDto.FixtureResponse response = new ApiFootballLiveDto.FixtureResponse();
        ApiFootballLiveDto.FixtureInfo fixtureInfo = new ApiFootballLiveDto.FixtureInfo();
        org.springframework.test.util.ReflectionTestUtils.setField(fixtureInfo, "id", 100L);
        org.springframework.test.util.ReflectionTestUtils.setField(response, "fixture", fixtureInfo);
        when(fixtureRepository.findAllBySeasonOrderByFixtureDateAsc(2025))
                .thenReturn(java.util.List.of(base));
        when(generationStore.create(2025, java.util.List.of(100L))).thenReturn(7L);
        when(generationStore.activeGenerationId(2025)).thenReturn(5L);
        when(generationReadStore.findFixture(5L, 100L)).thenReturn(Optional.of(published));
        when(apiFootballClient.getFixturesByIds(java.util.List.of(100L)))
                .thenReturn(java.util.List.of(response));
        when(fixtureSyncService.previewFixtureResponse(published, response)).thenReturn(published);

        service.stageSeasonFixtureResponses(2025, SyncProgressReporter.NO_OP);

        verify(fixtureSyncService).previewFixtureResponse(published, response);
        verify(fixtureRepository, never()).findByFixtureId(100L);
    }
}
