package com.son.soccerStreaming.repository;

import com.son.soccerStreaming.entity.Player;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface PlayerRepository extends JpaRepository<Player, Long> {
    Optional<Player> findByPlayerId(Long playerId);

    List<Player> findTop20ByNameContainingIgnoreCaseOrderByNameAsc(String keyword);
}
