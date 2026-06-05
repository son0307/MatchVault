package com.son.soccerStreaming.fixture.repository;

import com.son.soccerStreaming.fixture.entity.FixtureLineup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FixtureLineupRepository extends JpaRepository<FixtureLineup, Long> {

    @Query("select fl from FixtureLineup fl " +
            "join fetch fl.player p " +
            "join fetch fl.team t " +
            "where fl.fixture.fixtureId = :fixtureId")
    List<FixtureLineup> findAllByFixtureId(@Param("fixtureId") Long fixtureId);

    Optional<FixtureLineup> findByFixtureFixtureIdAndTeamTeamIdAndPlayerPlayerId(Long fixtureId, Long teamId, Long playerId);

    @Query("select fl.jerseyNumber from FixtureLineup fl " +
            "where fl.player.playerId = :playerId " +
            "and fl.team.teamId = :teamId " +
            "and fl.fixture.season = :season " +
            "and fl.jerseyNumber is not null " +
            "order by fl.fixture.fixtureDate desc, fl.fixture.fixtureId desc")
    List<Integer> findLineupNumbersByPlayerTeamAndSeason(
            @Param("playerId") Long playerId,
            @Param("teamId") Long teamId,
            @Param("season") Integer season
    );

    @Query("select max(fl.fixture.fixtureDate) from FixtureLineup fl where fl.player.playerId = :playerId")
    Optional<LocalDateTime> findLatestFixtureDateByPlayerId(@Param("playerId") Long playerId);
}
