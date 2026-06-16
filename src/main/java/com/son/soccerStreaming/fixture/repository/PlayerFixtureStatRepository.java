package com.son.soccerStreaming.fixture.repository;

import com.son.soccerStreaming.fixture.entity.PlayerFixtureStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PlayerFixtureStatRepository extends JpaRepository<PlayerFixtureStat, Long> {

    List<PlayerFixtureStat> findAllByFixtureFixtureId(Long fixtureId);

    List<PlayerFixtureStat> findAllByPlayerPlayerIdOrderByFixtureFixtureDateDescFixtureFixtureIdDesc(Long playerId);

    List<PlayerFixtureStat> findAllByPlayerPlayerIdAndFixtureSeasonOrderByFixtureFixtureDateDescFixtureFixtureIdDesc(
            Long playerId,
            Integer season
    );

    Optional<PlayerFixtureStat> findTopByPlayerPlayerIdOrderByFixtureFixtureDateDescFixtureFixtureIdDesc(Long playerId);

    List<PlayerFixtureStat> findTop5ByPlayerPlayerIdOrderByFixtureFixtureDateDesc(Long playerId);

    @EntityGraph(attributePaths = {"player", "team", "fixture", "fixture.homeTeam", "fixture.awayTeam"})
    @Query("SELECT s FROM PlayerFixtureStat s " +
            "WHERE s.player.playerId = :playerId " +
            "AND s.fixture.season = :season " +
            "AND (s.fixture.fixtureStatus = 'FINISHED' OR s.fixture.statusShort IN :finishedStatusShorts) " +
            "ORDER BY s.fixture.fixtureDate DESC, s.fixture.fixtureId DESC")
    List<PlayerFixtureStat> findRecentFinishedByPlayerId(
            @Param("playerId") Long playerId,
            @Param("season") Integer season,
            @Param("finishedStatusShorts") Collection<String> finishedStatusShorts,
            org.springframework.data.domain.Pageable pageable
    );

    Optional<PlayerFixtureStat> findByFixtureFixtureIdAndPlayerPlayerId(Long fixtureId, Long playerId);

    @Query("SELECT s.player.playerId as playerId, s.team.teamId as teamId, " +
            "s.fixture.fixtureDate as fixtureDate, s.fixture.fixtureId as fixtureId " +
            "FROM PlayerFixtureStat s " +
            "WHERE s.player.playerId IN :playerIds " +
            "AND s.fixture.season = :season " +
            "ORDER BY s.player.playerId ASC, s.fixture.fixtureDate DESC, s.fixture.fixtureId DESC")
    List<LatestPlayerTeam> findTeamHistoryByPlayerIdsAndSeasonOrderByLatest(
            @Param("playerIds") List<Long> playerIds,
            @Param("season") Integer season
    );

    interface LatestPlayerTeam {
        Long getPlayerId();
        Long getTeamId();
        LocalDateTime getFixtureDate();
        Long getFixtureId();
    }
}
