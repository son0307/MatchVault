package com.son.soccerStreaming.apifootball.scheduler;

import com.son.soccerStreaming.apifootball.service.ApiFootballSyncExecutionGuard;
import com.son.soccerStreaming.apifootball.service.FixtureDetailGenerationCleanupService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FixtureDetailGenerationCleanupSchedulerTest {

    @Test
    void runsWithTheMinimumRetentionAndProcessesOneGenerationAtATime() {
        var cleanupService = mock(FixtureDetailGenerationCleanupService.class);
        var scheduler = new FixtureDetailGenerationCleanupScheduler(
                cleanupService, new ApiFootballSyncExecutionGuard());
        ReflectionTestUtils.setField(scheduler, "retentionDays", 1);
        ReflectionTestUtils.setField(scheduler, "retiredToKeep", 0);
        ReflectionTestUtils.setField(scheduler, "maxPerRun", 100);
        var candidate = new FixtureDetailGenerationCleanupService.Candidate(7L, 2025);
        when(cleanupService.findCandidates(7, 2, 5)).thenReturn(List.of(candidate));
        when(cleanupService.deleteIfEligible(candidate, 7, 2)).thenReturn(true);

        scheduler.cleanup();

        verify(cleanupService).findCandidates(7, 2, 5);
        verify(cleanupService).deleteIfEligible(candidate, 7, 2);
    }
}
