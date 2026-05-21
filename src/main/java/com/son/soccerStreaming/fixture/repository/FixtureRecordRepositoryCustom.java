package com.son.soccerStreaming.fixture.repository;

import com.son.soccerStreaming.fixture.entity.Fixture;

import java.time.LocalDateTime;
import java.util.List;

public interface FixtureRecordRepositoryCustom {
    List<Fixture> findRecentFixturesWithCursor(Long cursorId, Integer season,
                                                LocalDateTime startDateTime, LocalDateTime endDateTime,
                                                int size);
}
