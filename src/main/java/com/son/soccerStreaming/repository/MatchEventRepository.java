package com.son.soccerStreaming.repository;

import com.son.soccerStreaming.entity.MatchEvent;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MatchEventRepository extends JpaRepository<MatchEvent, Long> {

    @EntityGraph(attributePaths = {"team", "player", "assistPlayer"})
    List<MatchEvent> findAllByMatchRecordApiFixtureIdOrderByElapsedAscEventSequenceAsc(Long apiFixtureId);
}
