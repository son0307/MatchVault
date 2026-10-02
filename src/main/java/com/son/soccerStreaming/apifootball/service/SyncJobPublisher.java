package com.son.soccerStreaming.apifootball.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
public class SyncJobPublisher {
    private final RedisSyncJobStream stream;
    private final ObjectMapper objectMapper;

    @Value("${api-football.sync.redis-queue.enabled:false}")
    private boolean enabled;

    @Value("${api-football.sync.distributed-lock.enabled:false}")
    private boolean distributedLockEnabled;

    @PostConstruct
    void validate() {
        if (enabled && !distributedLockEnabled) {
            throw new IllegalStateException("Redis sync queue requires the distributed sync lock");
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public String payload(SyncJobPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Could not serialize sync job payload", exception);
        }
    }

    public void publishAdmin(Long jobId, String task, String syncKey, String payload) {
        stream.ensureConsumerGroup();
        stream.addAdminOnce(new SyncQueueMessage("admin-" + jobId, task, syncKey, payload));
    }

    public void publishScheduled(String task, String syncKey, SyncJobPayload payload, ChronoUnit period) {
        stream.ensureConsumerGroup();
        Instant occurrence = Instant.now().truncatedTo(period);
        String id = task + ":" + syncKey + ":" + occurrence;
        stream.addScheduledOnce(new SyncQueueMessage(id, task, syncKey, payload(payload)));
    }
}
