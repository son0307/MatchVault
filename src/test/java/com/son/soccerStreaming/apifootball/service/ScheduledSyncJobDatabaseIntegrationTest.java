package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.apifootball.entity.ScheduledSyncJob;
import com.son.soccerStreaming.apifootball.entity.ScheduledSyncJobStatus;
import com.son.soccerStreaming.apifootball.repository.ScheduledSyncJobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Run only against an explicitly selected disposable MySQL test database. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = ScheduledSyncJobDatabaseIntegrationTest.JpaTestApplication.class)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=update")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(named = "SCHEDULED_SYNC_JOB_DB_INTEGRATION", matches = "true")
class ScheduledSyncJobDatabaseIntegrationTest {
    @SpringBootConfiguration
    @EntityScan(basePackageClasses = ScheduledSyncJob.class)
    @EnableJpaRepositories(basePackageClasses = ScheduledSyncJobRepository.class)
    @Import(ScheduledSyncJobService.class)
    static class JpaTestApplication {
    }

    @Autowired
    private ScheduledSyncJobService jobs;

    @Autowired
    private ScheduledSyncJobRepository repository;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("SCHEDULED_SYNC_JOB_TEST_DB_URL"));
        properties.add("spring.datasource.username", () -> System.getenv("SCHEDULED_SYNC_JOB_TEST_DB_USERNAME"));
        properties.add("spring.datasource.password", () -> System.getenv("SCHEDULED_SYNC_JOB_TEST_DB_PASSWORD"));
    }

    @Test
    void recordsStateAndPreventsConcurrentJobsForTheSameSyncKey() {
        String suffix = UUID.randomUUID().toString();
        String key = "teams:test:" + suffix;
        SyncQueueMessage first = new SyncQueueMessage("job-first-" + suffix, "teams", key, "{}");
        SyncQueueMessage second = new SyncQueueMessage("job-second-" + suffix, "teams", key, "{}");

        try {
            jobs.enqueue(first);
            assertThatThrownBy(() -> jobs.enqueue(second))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(jobs.start(first.jobId())).isTrue();
            assertThat(jobs.fail(first.jobId(), 3)).isEqualTo(1);
            assertThat(jobs.start(first.jobId())).isTrue();
            jobs.succeed(first.jobId());
            assertThat(repository.findById(first.jobId()).orElseThrow().getStatus())
                    .isEqualTo(ScheduledSyncJobStatus.SUCCEEDED);
            jobs.enqueue(second);
            assertThat(repository.findById(second.jobId()).orElseThrow().getStatus())
                    .isEqualTo(ScheduledSyncJobStatus.QUEUED);
        } finally {
            repository.deleteById(second.jobId());
            repository.deleteById(first.jobId());
        }
    }
}
