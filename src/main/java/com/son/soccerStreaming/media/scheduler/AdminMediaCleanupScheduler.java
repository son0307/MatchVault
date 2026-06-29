package com.son.soccerStreaming.media.scheduler;

import com.son.soccerStreaming.media.config.MediaProperties;
import com.son.soccerStreaming.media.service.AdminMediaCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "media.r2.enabled", havingValue = "true")
public class AdminMediaCleanupScheduler {

    private final AdminMediaCleanupService cleanupService;
    private final MediaProperties properties;

    @Scheduled(
            cron = "${media.admin-media-cleanup.cron:0 0 3 * * SUN}",
            zone = "${media.admin-media-cleanup.zone:Asia/Seoul}"
    )
    public void cleanupUnusedAdminMediaObjects() {
        if (!properties.getAdminMediaCleanup().isEnabled()) {
            return;
        }
        try {
            cleanupService.cleanupUnusedObjects();
        } catch (RuntimeException e) {
            log.error("[ADMIN MEDIA CLEANUP] cleanup failed before deletion completed.", e);
        }
    }
}
