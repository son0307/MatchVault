package com.son.soccerStreaming.player.repository;

import com.son.soccerStreaming.player.entity.Player;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface PlayerRepository extends JpaRepository<Player, Long> {
    Optional<Player> findByPlayerId(Long playerId);

    List<Player> findTop20ByNameContainingIgnoreCaseOrderByNameAsc(String keyword);
}
