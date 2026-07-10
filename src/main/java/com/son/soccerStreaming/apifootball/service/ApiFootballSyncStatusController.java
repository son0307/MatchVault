package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.apifootball.entity.ApiFootballSyncStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.son.soccerStreaming.auth.security.AuthUserDetails;
import com.son.soccerStreaming.global.exception.CustomException;
import com.son.soccerStreaming.global.exception.ErrorCode;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/sync")
public class ApiFootballSyncStatusController {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final Duration PUBLIC_SYNC_COOLDOWN = Duration.ofSeconds(30);

    private final ApiFootballSyncStatusService syncStatusService;
    private final ApiFootballStandingSyncService standingSyncService;
    private final ApiFootballFixtureSyncService fixtureSyncService;
    private final Map<String, Instant> publicSyncCooldowns = new ConcurrentHashMap<>();

    @GetMapping("/statuses")
    public SyncStatusResponse getStatuses(@RequestParam(defaultValue = "2025") Integer season) {
        return SyncStatusResponse.builder()
                .statuses(List.of(
                        status("standings", "Standings", "standings:%d".formatted(season)),
                        status("fixtures", "Fixtures", "fixtures:%d".formatted(season)),
                        status("fixture-details", "Season Details", "fixture-details:%d".formatted(season)),
                        status("players", "Players", "players:%d".formatted(season)),
                        status("injuries", "Injuries", "injuries:%d".formatted(season))
                ))
                .build();
    }

    @PostMapping("/standings")
    public PublicSyncResponse syncStandings(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @RequestParam(defaultValue = "39") Integer league,
            @RequestParam(defaultValue = "2025") Integer season
    ) {
        acquirePublicSync("standings:%d:%d".formatted(league, season));
        try {
            int count = standingSyncService.syncStandings(league, season);
            return PublicSyncResponse.builder()
                    .task("standings")
                    .success(true)
                    .count(count)
                    .message("Standings sync completed.")
                    .build();
        } catch (Exception exception) {
            syncStatusService.recordFailure("standings", "Standings", season, exception);
            throw exception;
        }
    }

    @PostMapping("/fixtures")
    public PublicSyncResponse syncFixtures(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @RequestParam(defaultValue = "39") Integer league,
            @RequestParam(defaultValue = "2025") Integer season
    ) {
        acquirePublicSync("fixtures:%d:%d".formatted(league, season));
        try {
            int count = fixtureSyncService.syncSeasonFixtures(league, season);
            return PublicSyncResponse.builder()
                    .task("fixtures")
                    .success(true)
                    .count(count)
                    .message("Fixtures sync completed.")
                    .build();
        } catch (Exception exception) {
            syncStatusService.recordFailure("fixtures", "Fixtures", season, exception);
            throw exception;
        }
    }

    private void acquirePublicSync(String key) {
        Instant now = Instant.now();
        publicSyncCooldowns.compute(key, (syncKey, current) -> {
            if (current != null && Duration.between(current, now).compareTo(PUBLIC_SYNC_COOLDOWN) < 0) {
                throw new CustomException(ErrorCode.ADMIN_SYNC_TOO_FREQUENT);
            }
            return now;
        });
    }

    private SyncStatusItem status(String task, String label, String syncKey) {
        return syncStatusService.findByKey(syncKey)
                .map(status -> toItem(task, label, status))
                .orElseGet(() -> SyncStatusItem.builder()
                        .task(task)
                        .label(label)
                        .failureCount(0)
                        .status("NEVER_SYNCED")
                        .build());
    }

    private SyncStatusItem toItem(String task, String label, ApiFootballSyncStatus status) {
        return SyncStatusItem.builder()
                .task(task)
                .label(label)
                .lastSyncedAt(status == null ? null : toKoreaOffsetDateTime(status.getLastSyncedAt()))
                .lastAttemptAt(status == null ? null : toKoreaOffsetDateTime(status.getLastAttemptAt()))
                .lastSuccessAt(status == null ? null : toKoreaOffsetDateTime(status.getLastSuccessAt()))
                .lastFailureAt(status == null ? null : toKoreaOffsetDateTime(status.getLastFailureAt()))
                .failureCount(syncFailureCount(status))
                .lastErrorMessage(status == null ? null : status.getLastErrorMessage())
                .status(syncDisplayStatus(status))
                .build();
    }

    private OffsetDateTime toKoreaOffsetDateTime(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atZone(KOREA_ZONE).toOffsetDateTime();
    }

    private String syncDisplayStatus(ApiFootballSyncStatus status) {
        if (status == null || status.getStatus() == null || status.getStatus().isBlank()) {
            return "NEVER_SYNCED";
        }
        if ("OK".equals(status.getStatus()) && status.getLastSuccessAt() != null
                && java.time.Duration.between(status.getLastSuccessAt(), LocalDateTime.now(KOREA_ZONE)).toHours() >= 24) {
            return "STALE";
        }
        return status.getStatus();
    }

    private int syncFailureCount(ApiFootballSyncStatus status) {
        if (status == null || status.getFailureCount() == null) {
            return 0;
        }
        return status.getFailureCount();
    }

    @Getter
    @Builder
    public static class SyncStatusResponse {
        private List<SyncStatusItem> statuses;
    }

    @Getter
    @Builder
    public static class SyncStatusItem {
        private String task;
        private String label;
        private OffsetDateTime lastSyncedAt;
        private OffsetDateTime lastAttemptAt;
        private OffsetDateTime lastSuccessAt;
        private OffsetDateTime lastFailureAt;
        private Integer failureCount;
        private String lastErrorMessage;
        private String status;
    }

    @Getter
    @Builder
    public static class PublicSyncResponse {
        private String task;
        private boolean success;
        private int count;
        private String message;
    }
}
