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
}
