package com.son.soccerStreaming.repository;

import com.son.soccerStreaming.entity.MatchRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MatchRecordRepository extends JpaRepository<MatchRecord, Long> {
    Optional<MatchRecord> findByMatchId(String matchId);
}
