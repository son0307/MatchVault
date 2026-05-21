package com.son.soccerStreaming.live.service;

import com.son.soccerStreaming.fixture.service.FixtureRedisService;

import com.son.soccerStreaming.fixture.service.FixturePlayerStatService;

import com.son.soccerStreaming.fixture.service.FixtureEventService;

import com.son.soccerStreaming.fixture.dto.FixtureEventDto;
import com.son.soccerStreaming.fixture.dto.FixturePlayerStatResponseDto;
import com.son.soccerStreaming.live.dto.LiveFixtureSnapshotDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveFixtureBroadcastService {

    private final FixtureRedisService fixtureRedisService;
    private final LiveFixtureSnapshotService liveFixtureSnapshotService;
    private final FixtureEventService fixtureEventService;
    private final FixturePlayerStatService fixturePlayerStatService;
    private final SseService sseService;
    private final ObjectMapper objectMapper;

    public void broadcastFixture(Long fixtureId, FixtureEventDto latestEvent) {
        LiveFixtureSnapshotDto snapshot = liveFixtureSnapshotService.rebuildAndCacheSnapshot(fixtureId, latestEvent);
        FixturePlayerStatResponseDto playerStats = fixturePlayerStatService.getFixturePlayerStats(fixtureId);
        fixtureRedisService.savePlayerStats(playerStats);

        broadcast(fixtureId, "LIVE_SNAPSHOT", snapshot);
        broadcast(fixtureId, "FIXTURE_EVENTS", fixtureEventService.getFixtureEvents(fixtureId));
        broadcast(fixtureId, "PLAYER_STATS", playerStats);
    }

    private void broadcast(Long fixtureId, String eventName, Object payload) {
        try {
            sseService.broadcastToFixture(String.valueOf(fixtureId), eventName, objectMapper.writeValueAsString(payload));
        } catch (JacksonException e) {
            log.error("Failed to serialize SSE payload. fixtureId={}, eventName={}", fixtureId, eventName, e);
        }
    }
}
