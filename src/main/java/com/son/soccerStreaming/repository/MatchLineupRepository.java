package com.son.soccerStreaming.repository;

import com.son.soccerStreaming.entity.MatchLineup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MatchLineupRepository extends JpaRepository<MatchLineup, Long> {

    @Query("select ml from MatchLineup ml " +
            "join fetch ml.player p " +
            "where ml.matchRecord.matchId = :matchId")
    List<MatchLineup> findAllByMatchId(@Param("matchId") String matchId);
}
