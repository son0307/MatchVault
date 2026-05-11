package com.son.soccerStreaming.service;

import com.son.soccerStreaming.apifootball.service.ApiFootballFixtureDetailSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveFixtureSyncService {

    private final ApiFootballFixtureDetailSyncService apiFootballFixtureDetailSyncService;
    private final LiveFixtureBroadcastService liveFixtureBroadcastService;

    @Transactional
    public void syncFixture(Long fixtureId) {
        ApiFootballFixtureDetailSyncService.FixtureDetailSyncResult result =
                apiFootballFixtureDetailSyncService.syncFixtureDetail(fixtureId, true);
        liveFixtureBroadcastService.broadcastFixture(fixtureId, result.latestEvent());
    }
}
