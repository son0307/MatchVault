package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.apifootball.entity.ApiFootballSyncStatus;
import com.son.soccerStreaming.apifootball.repository.ApiFootballSyncStatusRepository;
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
        recordSuccess(syncKey, displayName, null);
    }

    @Transactional
    public void recordSuccess(String syncKey, String displayName, Integer season) {
        LocalDateTime now = LocalDateTime.now(KOREA_ZONE);
        String seasonKey = syncKey(syncKey, season);
        ApiFootballSyncStatus status = apiFootballSyncStatusRepository.findById(seasonKey)
                .orElseGet(() -> ApiFootballSyncStatus.builder()
                        .syncKey(seasonKey)
                        .displayName(displayName(displayName, season))
                        .lastSyncedAt(now)
                        .build());
        status.recordSuccess(displayName(displayName, season), now);
        apiFootballSyncStatusRepository.save(status);
    }

    private String syncKey(String syncKey, Integer season) {
        return season == null ? syncKey : "%s:%d".formatted(syncKey, season);
    }

    private String displayName(String displayName, Integer season) {
        return season == null ? displayName : "%s %d".formatted(displayName, season);
    }
}
