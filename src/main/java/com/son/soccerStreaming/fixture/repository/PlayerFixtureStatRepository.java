package com.son.soccerStreaming.fixture.repository;

import com.son.soccerStreaming.fixture.entity.PlayerFixtureStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlayerFixtureStatRepository extends JpaRepository<PlayerFixtureStat, Long> {

    List<PlayerFixtureStat> findAllByFixtureFixtureId(Long fixtureId);

    List<PlayerFixtureStat> findAllByPlayerPlayerIdOrderByFixtureFixtureDateDesc(Long playerId);

    List<PlayerFixtureStat> findTop5ByPlayerPlayerIdOrderByFixtureFixtureDateDesc(Long playerId);

    Optional<PlayerFixtureStat> findByFixtureFixtureIdAndPlayerPlayerId(Long fixtureId, Long playerId);

    @Query("SELECT s.player.playerId as playerId, s.team.teamId as teamId " +
            "FROM PlayerFixtureStat s " +
            "WHERE s.player.playerId IN :playerIds " +
            "AND s.fixture.season = :season " +
            "AND s.fixture.fixtureDate = (" +
            "    SELECT MAX(latest.fixture.fixtureDate) " +
            "    FROM PlayerFixtureStat latest " +
            "    WHERE latest.player.playerId = s.player.playerId " +
            "    AND latest.fixture.season = :season" +
            ")")
    List<LatestPlayerTeam> findLatestTeamsByPlayerIdsAndSeason(
            @Param("playerIds") List<Long> playerIds,
            @Param("season") Integer season
    );

    interface LatestPlayerTeam {
        Long getPlayerId();
        Long getTeamId();
    }
}
