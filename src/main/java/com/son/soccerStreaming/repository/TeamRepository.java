package com.son.soccerStreaming.repository;

import com.son.soccerStreaming.entity.Team;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {

    @EntityGraph(attributePaths = "venue")
    Optional<Team> findByTeamApiId(Long teamId);

    List<Team> findAllByOrderByNameAsc();
}
