package com.son.soccerStreaming.repository;

import com.son.soccerStreaming.entity.PlayerAbsence;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlayerAbsenceRepository extends JpaRepository<PlayerAbsence, Long> {

    @EntityGraph(attributePaths = {"player", "team"})
    List<PlayerAbsence> findAllByMatchRecordApiFixtureId(Long apiFixtureId);
}
