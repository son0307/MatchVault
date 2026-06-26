package com.son.soccerStreaming.fixture.repository;

import com.son.soccerStreaming.fixture.entity.Fixture;

import java.time.LocalDateTime;
import java.util.List;

public interface FixtureRepositoryCustom {
    List<Fixture> findRecentFixturesWithCursor(Long cursorId, Integer season, Integer round,
                                                LocalDateTime startDateTime, LocalDateTime endDateTime,
                                                Long teamId, int size);

    List<Fixture> searchByTeamNameTokens(List<String> tokens, int size);

    List<Fixture> findHeadToHeadFixtures(Long fixtureId, Integer leagueId, Long homeTeamId, Long awayTeamId,
                                          List<String> finishedStatuses, int limit);
}
