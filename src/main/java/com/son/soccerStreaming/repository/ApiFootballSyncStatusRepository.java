package com.son.soccerStreaming.repository;

import com.son.soccerStreaming.entity.ApiFootballSyncStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiFootballSyncStatusRepository extends JpaRepository<ApiFootballSyncStatus, String> {
}
