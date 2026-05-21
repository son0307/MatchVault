package com.son.soccerStreaming.live.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "live.sync.enabled", havingValue = "true")
public class LiveFixturePollingScheduler {

    private final LiveFixtureSyncService liveFixtureSyncService;

    @Value("${live.sync.fixture-ids:}")
    private String fixtureIds;

    @Scheduled(fixedDelayString = "${live.sync.interval-ms:5000}")
    public void pollLiveFixtures() {
        for (Long fixtureId : targetFixtureIds()) {
            try {
                liveFixtureSyncService.syncFixture(fixtureId);
            } catch (Exception e) {
                log.error("Live fixture sync failed. fixtureId={}", fixtureId, e);
            }
        }
    }

    private List<Long> targetFixtureIds() {
        if (fixtureIds == null || fixtureIds.isBlank()) {
            return List.of();
        }
        return Arrays.stream(fixtureIds.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(Long::valueOf)
                .toList();
    }
}
