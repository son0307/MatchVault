package com.son.soccerStreaming.player.repository;

import com.son.soccerStreaming.player.entity.PlayerAbsence;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlayerAbsenceRepository extends JpaRepository<PlayerAbsence, Long> {

    @EntityGraph(attributePaths = {"player", "team"})
    List<PlayerAbsence> findAllByFixtureFixtureId(Long fixtureId);

    Optional<PlayerAbsence> findByPlayerPlayerIdAndFixtureFixtureId(Long playerId, Long fixtureId);
}
