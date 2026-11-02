package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.apifootball.entity.ScheduledSyncJob;
import com.son.soccerStreaming.apifootball.entity.ScheduledSyncJobStatus;
import com.son.soccerStreaming.apifootball.repository.ScheduledSyncJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ScheduledSyncJobService {
    private final ScheduledSyncJobRepository repository;

    @Transactional(readOnly = true)
    public boolean alreadyQueued(SyncQueueMessage message) {
        return repository.existsById(message.jobId()) || repository.existsByActiveSyncKey(message.syncKey());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueue(SyncQueueMessage message) {
        repository.saveAndFlush(ScheduledSyncJob.queued(
                message.jobId(), message.task(), message.syncKey(), message.payload()));
    }

    @Transactional(readOnly = true)
    public List<ScheduledSyncJob> recoverableJobs() {
        return repository.findAllByStatusIn(List.of(ScheduledSyncJobStatus.QUEUED, ScheduledSyncJobStatus.RUNNING));
    }

    @Transactional(readOnly = true)
    public Optional<ScheduledSyncJob> find(String jobId) {
        return repository.findById(jobId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean start(String jobId) {
        ScheduledSyncJob job = repository.findByIdForUpdate(jobId).orElseThrow();
        if (job.getStatus() != ScheduledSyncJobStatus.QUEUED && job.getStatus() != ScheduledSyncJobStatus.RUNNING) {
            return false;
        }
        job.start();
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeed(String jobId) {
        ScheduledSyncJob job = repository.findByIdForUpdate(jobId).orElseThrow();
        if (job.getStatus() == ScheduledSyncJobStatus.RUNNING) {
            job.succeed();
        } else if (job.getStatus() != ScheduledSyncJobStatus.SUCCEEDED) {
            throw new IllegalStateException("Scheduled job was no longer running: " + jobId);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int fail(String jobId, int maxAttempts) {
        ScheduledSyncJob job = repository.findByIdForUpdate(jobId).orElseThrow();
        return job.fail(maxAttempts);
    }
}
