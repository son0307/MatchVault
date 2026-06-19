package com.son.soccerStreaming.player.service;

import com.son.soccerStreaming.player.event.PlayerSeasonStatRebuildEventListener;
import com.son.soccerStreaming.player.event.PlayerSeasonStatRebuildRequested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PlayerSeasonStatRebuildEventListenerTest {

    @Mock
    private PlayerTeamSeasonStatAggregationService aggregationService;

    @InjectMocks
    private PlayerSeasonStatRebuildEventListener listener;

    @Test
    void rebuildsAffectedFixtureAfterCommit() {
        PlayerSeasonStatRebuildRequested event = new PlayerSeasonStatRebuildRequested(39, 100L, 2025);

        listener.rebuildAfterFixtureCommit(event);

        verify(aggregationService).rebuildForFixture(39, 100L, 2025);
    }

    @Test
    void doesNotPropagateRebuildFailureToFixtureSyncCaller() {
        PlayerSeasonStatRebuildRequested event = new PlayerSeasonStatRebuildRequested(39, 100L, 2025);
        doThrow(new IllegalStateException("rebuild failed"))
                .when(aggregationService)
                .rebuildForFixture(39, 100L, 2025);

        assertThatCode(() -> listener.rebuildAfterFixtureCommit(event))
                .doesNotThrowAnyException();
    }
}
