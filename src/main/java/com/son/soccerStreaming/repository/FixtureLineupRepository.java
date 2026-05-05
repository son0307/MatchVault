package com.son.soccerStreaming.repository;

import com.son.soccerStreaming.entity.FixtureLineup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FixtureLineupRepository extends JpaRepository<FixtureLineup, Long> {

    @Query("select fl from FixtureLineup fl " +
            "join fetch fl.player p " +
            "join fetch fl.team t " +
            "where fl.fixture.fixtureId = :fixtureId")
    List<FixtureLineup> findAllByFixtureId(@Param("fixtureId") Long fixtureId);
}
