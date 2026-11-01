package com.son.soccerStreaming.apifootball.scheduler;

import com.son.soccerStreaming.apifootball.service.ApiFootballSyncExecutionGuard;
import com.son.soccerStreaming.apifootball.service.FixtureDetailGenerationCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = {
        "fixture-detail.generation-reads.enabled", "fixture-detail.generation-batch.enabled",
        "fixture-detail.generation-cleanup.enabled"
}, havingValue = "true")
public class FixtureDetailGenerationCleanupScheduler {

    private static final String CLEANUP_KEY = "fixture-detail-generation-cleanup:daily";

    private final FixtureDetailGenerationCleanupService cleanupService;
    private final ApiFootballSyncExecutionGuard executionGuard;

    @Value("${fixture-detail.generation-cleanup.retention-days:7}")
    private int retentionDays;
    @Value("${fixture-detail.generation-cleanup.retain-retired:2}")
    private int retiredToKeep;
    @Value("${fixture-detail.generation-cleanup.max-per-run:5}")
    private int maxPerRun;

    @Scheduled(cron = "${fixture-detail.generation-cleanup.cron:0 30 2 * * *}",
            zone = "${fixture-detail.generation-cleanup.zone:Asia/Seoul}")
    public void cleanup() {
        try {
            executionGuard.executeIfAvailable(CLEANUP_KEY, () -> {
                int deleted = 0;
                int days = Math.max(7, retentionDays);
                int retained = Math.max(2, retiredToKeep);
                int limit = Math.max(1, Math.min(5, maxPerRun));
                for (var candidate : cleanupService.findCandidates(days, retained, limit)) {
                    ApiFootballSyncExecutionGuard.checkCurrentLease();
                    if (cleanupService.deleteIfEligible(candidate, days, retained)) {
                        deleted++;
                    }
                }
                if (deleted > 0) {
                    log.info("Obsolete fixture detail generations deleted. count={}", deleted);
                }
            });
        } catch (RuntimeException exception) {
            log.error("Fixture detail generation cleanup failed; remaining generations will be retried.", exception);
        }
    }
}
