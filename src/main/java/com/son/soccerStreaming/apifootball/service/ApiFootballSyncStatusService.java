package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.entity.ApiFootballSyncStatus;
import com.son.soccerStreaming.repository.ApiFootballSyncStatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class ApiFootballSyncStatusService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final ApiFootballSyncStatusRepository apiFootballSyncStatusRepository;

    @Transactional
    public void recordSuccess(String syncKey, String displayName) {
        LocalDateTime now = LocalDateTime.now(KOREA_ZONE);
        ApiFootballSyncStatus status = apiFootballSyncStatusRepository.findById(syncKey)
                .orElseGet(() -> ApiFootballSyncStatus.builder()
                        .syncKey(syncKey)
                        .displayName(displayName)
                        .lastSyncedAt(now)
                        .build());
        status.recordSuccess(displayName, now);
        apiFootballSyncStatusRepository.save(status);
    }
}
