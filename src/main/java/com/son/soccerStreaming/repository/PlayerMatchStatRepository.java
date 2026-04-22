package com.son.soccerStreaming.repository;

import com.son.soccerStreaming.entity.PlayerMatchStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlayerMatchStatRepository extends JpaRepository<PlayerMatchStat, Long> {

    List<PlayerMatchStat> findAllByMatchRecordMatchId(String matchId);

    @Query("SELECT " +
            "COUNT(s) as totalMatches, " +
            "COALESCE(SUM(s.goals), 0) as goals, " +
            "COALESCE(SUM(s.assists), 0) as assists, " +
            "COALESCE(SUM(s.shots), 0) as shots, " +
            "COALESCE(SUM(s.shotsOnTarget), 0) as shotsOnTarget, " +
            "COALESCE(SUM(s.totalPasses), 0) as totalPasses, " +
            "COALESCE(SUM(s.successfulPasses), 0) as successfulPasses, " +
            "COALESCE(SUM(s.fouls), 0) as fouls, " +
            "COALESCE(SUM(s.tackles), 0) as tackles " +
            "FROM PlayerMatchStat s " +
            "WHERE s.player.playerId = :playerId")
    SeasonStatSummary findSeasonStatSummaryByPlayerId(@Param("playerId") String playerId);

    interface SeasonStatSummary {
        int getTotalMatches();
        int getGoals();
        int getAssists();
        int getShots();
        int getShotsOnTarget();
        int getTotalPasses();
        int getSuccessfulPasses();
        int getFouls();
        int getTackles();
    }
}
