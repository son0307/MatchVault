package com.son.soccerStreaming.player.repository;

import com.son.soccerStreaming.player.entity.PlayerTeamSeasonStat;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlayerTeamSeasonStatRepository extends JpaRepository<PlayerTeamSeasonStat, Long> {

    Optional<PlayerTeamSeasonStat> findByPlayerPlayerIdAndTeamTeamIdAndLeagueIdAndSeason(
            Long playerId,
            Long teamId,
            Long leagueId,
            Integer season
    );

    List<PlayerTeamSeasonStat> findAllByPlayerPlayerIdAndTeamTeamIdAndSeason(
            Long playerId,
            Long teamId,
            Integer season
    );

    List<PlayerTeamSeasonStat> findAllByPlayerPlayerIdOrderBySeasonDesc(Long playerId);

    Optional<PlayerTeamSeasonStat> findTopByPlayerPlayerIdOrderBySeasonDesc(Long playerId);

    @EntityGraph(attributePaths = "team")
    List<PlayerTeamSeasonStat> findAllByPlayerPlayerIdAndSeason(Long playerId, Integer season);

    @Query("SELECT s FROM PlayerTeamSeasonStat s " +
            "JOIN FETCH s.player p " +
            "JOIN FETCH s.team t " +
            "WHERE s.leagueId = :leagueId AND s.season = :season " +
            "AND COALESCE(s.appearances, 0) > 0")
    List<PlayerTeamSeasonStat> findAllForLeagueRankings(
            @Param("leagueId") Long leagueId,
            @Param("season") Integer season
    );

    // Only expose players who appeared or were named on the bench at least once.
    @Query("SELECT s FROM PlayerTeamSeasonStat s " +
            "JOIN FETCH s.player p " +
            "WHERE s.team.teamId = :teamId AND s.season = :season " +
            "AND (COALESCE(s.appearances, 0) > 0 OR COALESCE(s.substitutesBench, 0) > 0) " +
            "ORDER BY p.name ASC")
    List<PlayerTeamSeasonStat> findAllByTeamAndSeason(
            @Param("teamId") Long teamId,
            @Param("season") Integer season
    );
}
