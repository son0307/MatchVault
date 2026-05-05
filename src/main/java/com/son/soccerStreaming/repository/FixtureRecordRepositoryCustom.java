package com.son.soccerStreaming.repository;

import com.son.soccerStreaming.entity.Fixture;

import java.util.List;

public interface FixtureRecordRepositoryCustom {
    List<Fixture> findRecentFixturesWithCursor(Long cursorId, int size);
}
