package com.son.soccerStreaming.favorite.repository;

import com.son.soccerStreaming.favorite.entity.FavoriteTeam;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FavoriteTeamRepository extends JpaRepository<FavoriteTeam, Long> {

    boolean existsByUserIdAndTeamTeamId(Long userId, Long teamId);

    void deleteByUserIdAndTeamTeamId(Long userId, Long teamId);

    @EntityGraph(attributePaths = "team")
    List<FavoriteTeam> findAllByUserIdOrderByCreatedAtDesc(Long userId);
}
