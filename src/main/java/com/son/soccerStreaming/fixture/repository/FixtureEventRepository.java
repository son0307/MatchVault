package com.son.soccerStreaming.fixture.repository;

import com.son.soccerStreaming.fixture.entity.FixtureEvent;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FixtureEventRepository extends JpaRepository<FixtureEvent, Long> {

    @EntityGraph(attributePaths = {"team", "player", "assistPlayer"})
    List<FixtureEvent> findAllByFixtureFixtureIdOrderByElapsedAscEventSequenceAsc(Long fixtureId);

    Optional<FixtureEvent> findByFixtureFixtureIdAndEventSequence(Long fixtureId, Integer eventSequence);
}
