package com.son.soccerStreaming.apifootball.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Applies committed live/admin changes to the active generation for player rankings. */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = {
        "fixture-detail.generation-reads.enabled", "fixture-detail.generation-batch.enabled"
}, havingValue = "true")
public class FixtureDetailLiveProjectionScheduler {

    private final FixtureDetailGenerationStore generationStore;
    private final FixtureDetailGenerationPublisher generationPublisher;

    @Scheduled(fixedDelayString = "${fixture-detail.live-projection-interval-ms:10000}")
    public void project() {
        for (Integer season : generationStore.activeSeasons()) {
            try {
                generationPublisher.projectLiveChanges(season);
            } catch (RuntimeException exception) {
                log.error("Fixture detail live projection will retry. season={}", season, exception);
            }
        }
    }
}
