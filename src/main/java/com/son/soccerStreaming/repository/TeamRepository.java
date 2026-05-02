package com.son.soccerStreaming.repository;

import com.son.soccerStreaming.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {
    Optional<Long> findByTeamApiId(Long teamId);
}
