package com.son.soccerStreaming.repository;

import com.son.soccerStreaming.entity.Fixture;

import java.time.LocalDateTime;
import java.util.List;

public interface FixtureRecordRepositoryCustom {
    List<Fixture> findRecentFixturesWithCursor(Long cursorId, Integer season,
                                                LocalDateTime startDateTime, LocalDateTime endDateTime,
                                                int size);
}
