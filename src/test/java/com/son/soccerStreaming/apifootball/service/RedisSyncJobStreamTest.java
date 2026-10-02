package com.son.soccerStreaming.apifootball.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisSyncJobStreamTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final StreamOperations<String, String, String> streams = mock(StreamOperations.class);
    private final RedisSyncJobStream queue = new RedisSyncJobStream(redis);

    @Test
    void writesADataOnlyCommandToTheStream() {
        doReturn(streams).when(redis).opsForStream();
        SyncQueueMessage message = new SyncQueueMessage("42", "players", "players:league=39; season=2026", "{}");
        RecordId id = RecordId.of("1-0");
        when(streams.add(eq(RedisSyncJobStream.STREAM), any(Map.class))).thenReturn(id);

        assertThat(queue.add(message)).isEqualTo(id);
        verify(streams).add(RedisSyncJobStream.STREAM, Map.of(
                "jobId", "42", "task", "players",
                "syncKey", "players:league=39; season=2026", "payload", "{}"));
    }

    @Test
    void claimsAnIdleUnacknowledgedCommandForAnotherConsumer() {
        doReturn(streams).when(redis).opsForStream();
        RecordId id = RecordId.of("1-0");
        Duration idle = Duration.ofMinutes(3);
        PendingMessage pending = new PendingMessage(id, Consumer.from(RedisSyncJobStream.GROUP, "blue"), idle, 1);
        when(streams.pending(eq(RedisSyncJobStream.STREAM), eq(RedisSyncJobStream.GROUP),
                any(), eq(5L), eq(Duration.ofMinutes(2))))
                .thenReturn(new PendingMessages(RedisSyncJobStream.GROUP, List.of(pending)));
        @SuppressWarnings("unchecked")
        MapRecord<String, String, String> record = mock(MapRecord.class);
        when(record.getId()).thenReturn(id);
        when(record.getValue()).thenReturn(Map.of("jobId", "42", "task", "players", "syncKey", "players:39", "payload", "{}"));
        when(streams.claim(RedisSyncJobStream.STREAM, RedisSyncJobStream.GROUP,
                "green", Duration.ofMinutes(2), id)).thenReturn(List.of(record));

        var jobs = queue.claimIdle("green", Duration.ofMinutes(2), 5);

        assertThat(jobs).hasSize(1);
        assertThat(jobs.get(0).message().jobId()).isEqualTo("42");
    }

    @Test
    void readsANewCommandWithoutAcknowledgingIt() {
        doReturn(streams).when(redis).opsForStream();
        @SuppressWarnings("unchecked")
        MapRecord<String, String, String> record = mock(MapRecord.class);
        when(record.getId()).thenReturn(RecordId.of("2-0"));
        when(record.getValue()).thenReturn(Map.of(
                "jobId", "43", "task", "injuries", "syncKey", "injuries:39", "payload", "{}"));
        when(streams.read(any(Consumer.class),
                any(org.springframework.data.redis.connection.stream.StreamReadOptions.class),
                any(org.springframework.data.redis.connection.stream.StreamOffset.class)))
                .thenReturn(List.of(record));

        var jobs = queue.readNew("blue", Duration.ofSeconds(1));

        assertThat(jobs).hasSize(1);
        assertThat(jobs.get(0).message().task()).isEqualTo("injuries");
    }

    @Test
    void acknowledgesOnlyAPendingCommand() {
        doReturn(streams).when(redis).opsForStream();
        RecordId id = RecordId.of("1-0");
        when(streams.acknowledge(RedisSyncJobStream.STREAM, RedisSyncJobStream.GROUP, id)).thenReturn(1L);
        queue.acknowledge(id);

        when(streams.acknowledge(RedisSyncJobStream.STREAM, RedisSyncJobStream.GROUP, id)).thenReturn(0L);
        assertThatThrownBy(() -> queue.acknowledge(id)).isInstanceOf(IllegalStateException.class);
    }
}
