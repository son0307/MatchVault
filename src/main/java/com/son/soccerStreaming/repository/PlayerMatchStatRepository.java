package com.son.soccerStreaming.repository;

import com.son.soccerStreaming.entity.PlayerMatchStat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerMatchStatRepository extends JpaRepository<PlayerMatchStat, Long> {
}
