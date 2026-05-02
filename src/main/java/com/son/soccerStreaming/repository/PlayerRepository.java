package com.son.soccerStreaming.repository;

import com.son.soccerStreaming.entity.Player;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlayerRepository extends JpaRepository<Player, Long> {
    List<Player> findAllByTeamTeamApiId(Long teamId);
    Optional<Player> findByApiPlayerId(Long playerId);
}
