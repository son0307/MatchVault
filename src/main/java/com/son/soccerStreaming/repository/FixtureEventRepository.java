package com.son.soccerStreaming.repository;

import com.son.soccerStreaming.entity.FixtureEvent;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FixtureEventRepository extends JpaRepository<FixtureEvent, Long> {

    @EntityGraph(attributePaths = {"team", "player", "assistPlayer"})
    List<FixtureEvent> findAllByFixtureFixtureIdOrderByElapsedAscEventSequenceAsc(Long fixtureId);
}
