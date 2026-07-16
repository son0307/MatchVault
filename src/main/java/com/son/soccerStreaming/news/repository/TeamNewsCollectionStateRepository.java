package com.son.soccerStreaming.news.repository;

import com.son.soccerStreaming.news.entity.TeamNewsCollectionState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamNewsCollectionStateRepository extends JpaRepository<TeamNewsCollectionState, Long> {
}
