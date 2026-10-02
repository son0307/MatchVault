package com.son.soccerStreaming.apifootball.service;

import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class RedisSyncJobStream {
    static final String STREAM = "match-vault:sync:jobs:v1";
    static final String GROUP = "sync-workers";

    private final StringRedisTemplate redis;
    private final String streamKey;
    private final String group;

    @Autowired
    public RedisSyncJobStream(StringRedisTemplate redis) {
        this(redis, STREAM, GROUP);
    }

    RedisSyncJobStream(StringRedisTemplate redis, String streamKey, String group) {
        this.redis = redis;
        this.streamKey = streamKey;
        this.group = group;
    }

    /** XGROUP CREATE with MKSTREAM is safe before the first job is published. */
    public void ensureConsumerGroup() {
        try {
            redis.execute((RedisCallback<String>) connection -> connection.streamCommands().xGroupCreate(
                    redis.getStringSerializer().serialize(streamKey), group, ReadOffset.from("0-0"), true));
        } catch (DataAccessException exception) {
            if (!hasBusyGroupCause(exception)) {
                throw exception;
            }
        }
    }

    public RecordId add(SyncQueueMessage message) {
        RecordId id = streams().add(streamKey, Map.of(
                "jobId", message.jobId(),
                "task", message.task(),
                "syncKey", message.syncKey(),
                "payload", message.payload()));
        if (id == null) {
            throw new IllegalStateException("Redis did not return a stream record ID");
        }
        return id;
    }

    public void addAdminOnce(SyncQueueMessage message) {
        String script = """
                if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end
                redis.call('XADD', KEYS[2], '*', 'jobId', ARGV[1], 'task', ARGV[2],
                    'syncKey', ARGV[3], 'payload', ARGV[4])
                redis.call('SET', KEYS[1], '1', 'EX', 172800)
                return 1
                """;
        Long added = redis.execute(new DefaultRedisScript<>(script, Long.class),
                List.of(streamKey + ":admin-active:" + message.jobId(), streamKey),
                message.jobId(), message.task(), message.syncKey(), message.payload());
        if (added == null) {
            throw new IllegalStateException("Redis did not confirm admin sync enqueue");
        }
    }

    public void acknowledgeAdmin(QueuedSyncJob job) {
        String script = """
                local count = redis.call('XACK', KEYS[1], ARGV[1], ARGV[2])
                if count == 1 then redis.call('XDEL', KEYS[1], ARGV[2]) end
                redis.call('DEL', KEYS[2])
                return count
                """;
        Long count = redis.execute(new DefaultRedisScript<>(script, Long.class),
                List.of(streamKey, streamKey + ":admin-active:" + job.message().jobId()),
                group, job.id().getValue());
        if (count == null || count != 1L) {
            throw new IllegalStateException("Admin sync job was not pending when acknowledged: " + job.id());
        }
    }

    /** Deduplicate a scheduled firing atomically with XADD across blue/green instances. */
    public boolean addScheduledOnce(SyncQueueMessage message) {
        String script = """
                if redis.call('EXISTS', KEYS[1]) == 1 or redis.call('EXISTS', KEYS[3]) == 1 then return 0 end
                redis.call('XADD', KEYS[2], '*', 'jobId', ARGV[1], 'task', ARGV[2],
                    'syncKey', ARGV[3], 'payload', ARGV[4])
                redis.call('SET', KEYS[1], '1', 'EX', 172800)
                redis.call('SET', KEYS[3], ARGV[1], 'EX', 172800)
                return 1
                """;
        Long added = redis.execute(new DefaultRedisScript<>(script, Long.class),
                List.of(streamKey + ":scheduled:" + message.jobId(), streamKey,
                        streamKey + ":active:" + message.syncKey()),
                message.jobId(), message.task(), message.syncKey(), message.payload());
        if (added == null) {
            throw new IllegalStateException("Redis did not confirm scheduled sync enqueue");
        }
        return Long.valueOf(1).equals(added);
    }

    public void acknowledgeScheduled(QueuedSyncJob job) {
        String script = """
                local count = redis.call('XACK', KEYS[1], ARGV[1], ARGV[2])
                if count == 1 then redis.call('XDEL', KEYS[1], ARGV[2]) end
                if redis.call('GET', KEYS[2]) == ARGV[3] then redis.call('DEL', KEYS[2]) end
                return count
                """;
        Long count = redis.execute(new DefaultRedisScript<>(script, Long.class),
                List.of(streamKey, streamKey + ":active:" + job.message().syncKey()),
                group, job.id().getValue(), job.message().jobId());
        if (count == null || count != 1L) {
            throw new IllegalStateException("Scheduled sync job was not pending when acknowledged: " + job.id());
        }
    }

    /** Move a terminal failure to a separate stream and acknowledge it in one Redis operation. */
    public void deadLetter(QueuedSyncJob job) {
        String script = """
                redis.call('XADD', KEYS[2], 'MAXLEN', '~', 10000, '*', 'jobId', ARGV[3], 'task', ARGV[4],
                    'syncKey', ARGV[5], 'payload', ARGV[6])
                local count = redis.call('XACK', KEYS[1], ARGV[1], ARGV[2])
                if count == 1 then redis.call('XDEL', KEYS[1], ARGV[2]) end
                if redis.call('GET', KEYS[3]) == ARGV[3] then redis.call('DEL', KEYS[3]) end
                redis.call('DEL', KEYS[4])
                return count
                """;
        SyncQueueMessage message = job.message();
        Long count = redis.execute(new DefaultRedisScript<>(script, Long.class),
                List.of(streamKey, streamKey + ":dead", streamKey + ":active:" + message.syncKey(),
                        streamKey + ":admin-active:" + message.jobId()),
                group, job.id().getValue(), message.jobId(), message.task(), message.syncKey(), message.payload());
        if (count == null || count != 1L) {
            throw new IllegalStateException("Failed sync job was not pending when moved: " + job.id());
        }
    }

    public List<QueuedSyncJob> readNew(String consumerName, Duration wait) {
        List<MapRecord<String, String, String>> records = streams().read(
                Consumer.from(group, consumerName),
                StreamReadOptions.empty().count(1).block(wait),
                StreamOffset.create(streamKey, ReadOffset.lastConsumed()));
        return decode(records);
    }

    /** Redis retains unacknowledged entries; another consumer explicitly claims idle work. */
    public List<QueuedSyncJob> claimIdle(String consumerName, Duration minimumIdle, int limit) {
        var pending = streams().pending(streamKey, group, Range.unbounded(), limit, minimumIdle);
        if (pending == null || pending.isEmpty()) {
            return List.of();
        }
        RecordId[] ids = pending.stream().map(item -> item.getId()).toArray(RecordId[]::new);
        return decode(streams().claim(streamKey, group, consumerName, minimumIdle, ids));
    }

    public void acknowledge(RecordId id) {
        Long count = streams().acknowledge(streamKey, group, id);
        if (count == null || count != 1L) {
            throw new IllegalStateException("Sync job was not pending when acknowledged: " + id);
        }
        streams().delete(streamKey, id);
    }

    public boolean completed(String jobId) {
        return Boolean.TRUE.equals(redis.hasKey(streamKey + ":completed:" + jobId));
    }

    public void clearCompleted(String jobId) {
        redis.delete(streamKey + ":completed:" + jobId);
    }

    public void markCompleted(String jobId) {
        redis.opsForValue().set(streamKey + ":completed:" + jobId, "1", Duration.ofDays(2));
    }

    public long recordFailure(RecordId id) {
        String key = streamKey + ":failures:" + id;
        Long count = redis.opsForValue().increment(key);
        redis.expire(key, Duration.ofDays(2));
        return count == null ? 1 : count;
    }

    private StreamOperations<String, String, String> streams() {
        return redis.opsForStream();
    }

    private static List<QueuedSyncJob> decode(List<MapRecord<String, String, String>> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        return records.stream().map(record -> new QueuedSyncJob(record.getId(), new SyncQueueMessage(
                record.getValue().get("jobId"),
                record.getValue().get("task"),
                record.getValue().get("syncKey"),
                record.getValue().get("payload")))).toList();
    }

    private static boolean hasBusyGroupCause(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains("BUSYGROUP")) {
                return true;
            }
        }
        return false;
    }

    public record QueuedSyncJob(RecordId id, SyncQueueMessage message) {
    }
}
