package com.son.soccerStreaming.apifootball.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Run only against a local Redis explicitly selected for integration testing. */
@EnabledIfEnvironmentVariable(named = "REDIS_STREAM_INTEGRATION", matches = "true")
class RedisSyncJobStreamIntegrationTest {

    @Test
    void createsGroupReadsReclaimsAndAcknowledgesAnActualStream() throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("REDIS_STREAM_INTEGRATION_PORT", "6379"));
        LettuceConnectionFactory factory = new LettuceConnectionFactory("127.0.0.1", port);
        factory.afterPropertiesSet();
        StringRedisTemplate redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
        String streamKey = "codex:sync-stream-test:" + UUID.randomUUID();
        String group = "test-workers";
        RedisSyncJobStream stream = new RedisSyncJobStream(redis, streamKey, group);

        try {
            stream.ensureConsumerGroup();
            stream.ensureConsumerGroup();
            var id = stream.add(new SyncQueueMessage("job-1", "fixtures", "fixtures:39", "{}"));

            var received = stream.readNew("blue", Duration.ofSeconds(1));
            assertThat(received).hasSize(1);
            assertThat(received.get(0).id()).isEqualTo(id);
            assertThat(received.get(0).message().jobId()).isEqualTo("job-1");

            var reclaimed = stream.claimIdle("green", Duration.ZERO, 10);
            assertThat(reclaimed).hasSize(1);
            assertThat(reclaimed.get(0).id()).isEqualTo(id);
            assertThat(redis.opsForStream().pending(streamKey, group).getTotalPendingMessages()).isEqualTo(1);

            stream.acknowledge(id);
            assertThat(redis.opsForStream().pending(streamKey, group).getTotalPendingMessages()).isZero();

            var scheduled = new SyncQueueMessage("scheduled-1", "teams", "teams:39", "{}");
            assertThat(stream.addScheduledOnce(scheduled)).isTrue();
            assertThat(stream.addScheduledOnce(scheduled)).isFalse();
            assertThat(redis.opsForStream().size(streamKey)).isEqualTo(1L);
            var scheduledDelivery = stream.readNew("blue", Duration.ofSeconds(1));
            assertThat(scheduledDelivery).hasSize(1);
            stream.acknowledgeScheduled(scheduledDelivery.get(0));
            assertThat(redis.hasKey(streamKey + ":active:teams:39")).isFalse();
            assertThat(redis.opsForStream().size(streamKey)).isZero();

            stream.add(new SyncQueueMessage("failed-1", "injuries", "injuries:39", "{}"));
            var failedDelivery = stream.readNew("green", Duration.ofSeconds(1));
            stream.deadLetter(failedDelivery.get(0));
            assertThat(redis.opsForStream().size(streamKey + ":dead")).isEqualTo(1L);
            assertThat(redis.opsForStream().pending(streamKey, group).getTotalPendingMessages()).isZero();

            var admin = new SyncQueueMessage("admin-9", "players", "players:39", "{}");
            stream.addAdminOnce(admin);
            stream.addAdminOnce(admin);
            assertThat(redis.opsForStream().size(streamKey)).isEqualTo(1L);
            stream.acknowledgeAdmin(stream.readNew("blue", Duration.ofSeconds(1)).get(0));
            assertThat(redis.hasKey(streamKey + ":admin-active:admin-9")).isFalse();
        } finally {
            redis.delete(streamKey);
            redis.delete(streamKey + ":dead");
            redis.delete(streamKey + ":scheduled:scheduled-1");
            redis.delete(streamKey + ":active:teams:39");
            redis.delete(streamKey + ":admin-active:admin-9");
            factory.destroy();
        }
    }
}
