package com.son.soccerStreaming.live.service;

import com.son.soccerStreaming.fixture.service.FixtureEventService;
import com.son.soccerStreaming.fixture.service.FixturePlayerStatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class SseRecoverySnapshotService {
    private final LiveFixtureSnapshotService snapshots;
    private final FixtureEventService events;
    private final FixturePlayerStatService players;
    private final ObjectMapper mapper;

    // Recovery only: read one committed DB view without Redis caches.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, propagation = Propagation.REQUIRES_NEW)
    public Bundle read(long fixtureId) {
        return new Bundle(fixtureId, List.of(
                event("LIVE_SNAPSHOT", snapshots.readSnapshot(fixtureId, null)),
                event("FIXTURE_EVENTS", events.getFixtureEvents(fixtureId)),
                event("PLAYER_STATS", players.getFixturePlayerStats(fixtureId))));
    }
    public Event event(String name, Object value) {
        return new Event(name, mapper.writeValueAsString(value));
    }
    public record Event(String event, String data) {}
    public record Bundle(long fixtureId, List<Event> events) {}
}
