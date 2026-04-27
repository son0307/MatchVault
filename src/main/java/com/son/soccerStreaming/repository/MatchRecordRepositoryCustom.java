package com.son.soccerStreaming.repository;

import com.son.soccerStreaming.entity.MatchRecord;

import java.util.List;

public interface MatchRecordRepositoryCustom {
    List<MatchRecord> findRecentMatchesWithCursor(Long cursorId, int size);
}
