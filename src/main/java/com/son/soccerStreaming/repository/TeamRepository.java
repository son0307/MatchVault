package com.son.soccerStreaming.repository;

import com.son.soccerStreaming.entity.Team;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {

    @EntityGraph(attributePaths = "venue")
    Optional<Team> findByTeamId(Long teamId);

    List<Team> findAllByOrderByNameAsc();

    List<Team> findTop20ByNameContainingIgnoreCaseOrderByNameAsc(String keyword);
}
