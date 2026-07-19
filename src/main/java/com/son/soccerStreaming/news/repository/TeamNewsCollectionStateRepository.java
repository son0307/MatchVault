package com.son.soccerStreaming.news.repository;

import com.son.soccerStreaming.news.entity.TeamNewsCollectionState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TeamNewsCollectionStateRepository extends JpaRepository<TeamNewsCollectionState, Long> {
    Optional<TeamNewsCollectionState> findByTeamTeamId(Long teamId);
}
