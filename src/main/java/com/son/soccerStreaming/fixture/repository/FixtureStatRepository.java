package com.son.soccerStreaming.fixture.repository;

import com.son.soccerStreaming.fixture.entity.FixtureStat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FixtureStatRepository extends JpaRepository<FixtureStat, Long> {

    List<FixtureStat> findAllByFixtureFixtureId(Long fixtureId);

    Optional<FixtureStat> findByFixtureFixtureIdAndTeamTeamId(Long fixtureId, Long teamId);
}
