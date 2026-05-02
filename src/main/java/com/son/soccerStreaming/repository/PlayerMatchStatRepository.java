package com.son.soccerStreaming.repository;

import com.son.soccerStreaming.entity.PlayerMatchStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlayerMatchStatRepository extends JpaRepository<PlayerMatchStat, Long> {

    // 💡 변경: findAllByMatchRecordMatchId -> findAllByMatchRecordApiFixtureId
    List<PlayerMatchStat> findAllByMatchRecordApiFixtureId(Long apiFixtureId);

    // 💡 파라미터 타입(String -> Long) 및 새 필드명 반영 (Null-Safe 처리)
    @Query("SELECT " +
            "COUNT(s) as totalMatches, " +
            "COALESCE(SUM(s.goals), 0) as goals, " +
            "COALESCE(SUM(s.assists), 0) as assists, " +
            "COALESCE(SUM(s.shotsTotal), 0) as shotsTotal, " +
            "COALESCE(SUM(s.shotsOnTarget), 0) as shotsOnTarget, " +
            "COALESCE(SUM(s.passesTotal), 0) as passesTotal, " +
            "COALESCE(AVG(s.passAccuracy), 0) as avgPassAccuracy, " +
            "COALESCE(SUM(s.foulsCommitted), 0) as foulsCommitted, " +
            "COALESCE(SUM(s.tacklesTotal), 0) as tacklesTotal, " +
            "COALESCE(SUM(s.yellowCards), 0) as yellowCards, " +
            "COALESCE(SUM(s.redCards), 0) as redCards " +
            "FROM PlayerMatchStat s " +
            "WHERE s.player.apiPlayerId = :apiPlayerId")
    SeasonStatSummary findSeasonStatSummaryByPlayerId(@Param("apiPlayerId") Long apiPlayerId);

    // 💡 쿼리 반환값과 1:1로 매칭되는 인터페이스
    interface SeasonStatSummary {
        int getTotalMatches();
        int getGoals();
        int getAssists();
        int getShotsTotal();
        int getShotsOnTarget();
        int getPassesTotal();
        double getAvgPassAccuracy(); // % 평점
        int getFoulsCommitted();
        int getTacklesTotal();
        int getYellowCards();
        int getRedCards();
    }
}