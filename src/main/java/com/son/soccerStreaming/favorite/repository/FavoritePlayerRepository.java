package com.son.soccerStreaming.favorite.repository;

import com.son.soccerStreaming.favorite.entity.FavoritePlayer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FavoritePlayerRepository extends JpaRepository<FavoritePlayer, Long> {

    boolean existsByUserIdAndPlayerPlayerId(Long userId, Long playerId);

    void deleteByUserIdAndPlayerPlayerId(Long userId, Long playerId);

    @EntityGraph(attributePaths = "player")
    List<FavoritePlayer> findAllByUserIdOrderByCreatedAtDesc(Long userId);
}
