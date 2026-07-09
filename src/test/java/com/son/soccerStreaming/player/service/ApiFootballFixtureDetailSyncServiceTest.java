package com.son.soccerStreaming.player.service;

import com.son.soccerStreaming.apifootball.client.ApiFootballClient;
import com.son.soccerStreaming.apifootball.dto.ApiFootballLiveDto;
import com.son.soccerStreaming.apifootball.service.ApiFootballFixtureDetailSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballFixtureEventSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballFixtureLineupSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballFixturePlayerStatSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballFixtureStatSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballFixtureSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballSyncStatusService;
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

    @InjectMocks
    private ApiFootballFixtureDetailSyncService service;

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
}
