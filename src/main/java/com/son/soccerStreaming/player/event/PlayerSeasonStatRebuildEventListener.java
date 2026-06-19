package com.son.soccerStreaming.player.event;

import com.son.soccerStreaming.player.service.PlayerTeamSeasonStatAggregationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
@RequiredArgsConstructor
public class PlayerSeasonStatRebuildEventListener {

    private final PlayerTeamSeasonStatAggregationService aggregationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void rebuildAfterFixtureCommit(PlayerSeasonStatRebuildRequested event) {
        try {
            aggregationService.rebuildForFixture(event.leagueId(), event.fixtureId(), event.season());
        } catch (RuntimeException exception) {
            log.error(
                    "Player team season stat rebuild failed after fixture commit. league={}, fixtureId={}, season={}",
                    event.leagueId(),
                    event.fixtureId(),
                    event.season(),
                    exception
            );
        }
    }
}
