package com.son.soccerStreaming.apifootball.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SyncJobPublisherTest {
    @Test
    void persistsScheduledJobBeforePublishingToRedis() {
        RedisSyncJobStream stream = mock(RedisSyncJobStream.class);
        ScheduledSyncJobService jobs = mock(ScheduledSyncJobService.class);
        SyncJobPublisher publisher = new SyncJobPublisher(stream, jobs, new tools.jackson.databind.ObjectMapper());

        publisher.publishScheduled("teams", "teams:39", new SyncJobPayload(39, 2025), ChronoUnit.DAYS);

        ArgumentCaptor<SyncQueueMessage> message = ArgumentCaptor.forClass(SyncQueueMessage.class);
        InOrder order = inOrder(jobs, stream);
        order.verify(jobs).enqueue(message.capture());
        order.verify(stream).ensureConsumerGroup();
        order.verify(stream).addScheduledOnce(message.getValue());
        assertThat(message.getValue().jobId()).startsWith("teams:teams:39:");
    }

    @Test
    void duplicateActiveJobDoesNotPublishAnotherMessage() {
        RedisSyncJobStream stream = mock(RedisSyncJobStream.class);
        ScheduledSyncJobService jobs = mock(ScheduledSyncJobService.class);
        SyncJobPublisher publisher = new SyncJobPublisher(stream, jobs, new tools.jackson.databind.ObjectMapper());
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("duplicate"))
                .when(jobs).enqueue(any());
        when(jobs.alreadyQueued(any())).thenReturn(true);

        publisher.publishScheduled("teams", "teams:39", new SyncJobPayload(39, 2025), ChronoUnit.DAYS);

        verify(stream, never()).addScheduledOnce(any());
    }

    @Test
    void redisFailureLeavesTheDatabaseJobForRecovery() {
        RedisSyncJobStream stream = mock(RedisSyncJobStream.class);
        ScheduledSyncJobService jobs = mock(ScheduledSyncJobService.class);
        SyncJobPublisher publisher = new SyncJobPublisher(stream, jobs, new tools.jackson.databind.ObjectMapper());
        org.mockito.Mockito.doThrow(new IllegalStateException("redis unavailable"))
                .when(stream).ensureConsumerGroup();

        assertThatThrownBy(() -> publisher.publishScheduled(
                "teams", "teams:39", new SyncJobPayload(39, 2025), ChronoUnit.DAYS))
                .isInstanceOf(IllegalStateException.class);

        verify(jobs).enqueue(any());
    }
}
