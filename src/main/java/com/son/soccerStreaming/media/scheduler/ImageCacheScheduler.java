package com.son.soccerStreaming.media.scheduler;

import com.son.soccerStreaming.media.config.MediaProperties;
import com.son.soccerStreaming.media.service.ImageCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "media.cache.scheduler.enabled", havingValue = "true")
public class ImageCacheScheduler {

    private final ImageCacheService imageCacheService;
    private final MediaProperties properties;

    @Scheduled(cron = "${media.cache.scheduler.cron:0 30 * * * *}")
    public void cacheMissingImages() {
        try {
            imageCacheService.cacheMissingImages(properties.getCache().getScheduler().getBatchSize());
        } catch (Exception e) {
            log.error("Image cache scheduler failed.", e);
        }
    }
}
