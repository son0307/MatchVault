package com.son.soccerStreaming.apifootball.repository;

import com.son.soccerStreaming.apifootball.entity.ApiFootballSyncStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiFootballSyncStatusRepository extends JpaRepository<ApiFootballSyncStatus, String> {
}
