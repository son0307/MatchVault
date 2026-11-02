package com.son.soccerStreaming.apifootball.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduledSyncJobTest {
    @Test
    void interruptedJobCanStartAgainFromTheBeginning() {
        ScheduledSyncJob job = ScheduledSyncJob.queued("job-1", "teams", "teams:39", "{}");

        job.start();
        job.fail(3);
        job.start();
        job.succeed();

        assertThat(job.getStatus()).isEqualTo(ScheduledSyncJobStatus.SUCCEEDED);
        assertThat(job.getAttempts()).isEqualTo(1);
        assertThat(job.getActiveSyncKey()).isNull();
        assertThat(job.getCompletedAt()).isNotNull();
    }

    @Test
    void thirdExecutionFailureReleasesTheActiveKey() {
        ScheduledSyncJob job = ScheduledSyncJob.queued("job-2", "teams", "teams:39", "{}");

        assertThat(job.fail(3)).isEqualTo(1);
        assertThat(job.fail(3)).isEqualTo(2);
        assertThat(job.getStatus()).isEqualTo(ScheduledSyncJobStatus.QUEUED);
        assertThat(job.fail(3)).isEqualTo(3);

        assertThat(job.getStatus()).isEqualTo(ScheduledSyncJobStatus.FAILED);
        assertThat(job.getActiveSyncKey()).isNull();
    }
}
