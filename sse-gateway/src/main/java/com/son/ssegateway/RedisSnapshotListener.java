package com.son.ssegateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.*;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.LoggerFactory;

@Configuration
@ConditionalOnProperty(name = "sse.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedisSnapshotListener {
    @Bean org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor redisSubscriptionExecutor() {
        var executor = new org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1); executor.setMaxPoolSize(1); executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("redis-sse-subscribe-"); return executor;
    }
    @Bean RedisMessageListenerContainer snapshots(RedisConnectionFactory connectionFactory, SubscriberHub hub,
            ObjectMapper mapper, RedisRecovery recovery, @Value("${sse.redis.channel:soccer:sse:v1}") String channel) {
        var container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setSubscriptionExecutor(redisSubscriptionExecutor());
        // Preserve Redis delivery order; callback only offers into bounded mailboxes, never writes sockets.
        container.setTaskExecutor(Runnable::run);
        class Listener implements org.springframework.data.redis.connection.MessageListener, org.springframework.data.redis.connection.SubscriptionListener {
            @Override public void onChannelSubscribed(byte[] name, long count) { recovery.subscribed(); }
            @Override public void onMessage(org.springframework.data.redis.connection.Message message, byte[] pattern) {
            try {
                if (message.getBody().length > 20 * 1024 * 1024) throw new IllegalArgumentException();
                var envelope = mapper.readValue(message.getBody(), Envelope.class);
                if (envelope.fixtureId() <= 0) throw new IllegalArgumentException();
                hub.publish(envelope.fixtureId(), Snapshot.data(envelope.event(), envelope.data()));
            } catch (RuntimeException error) {
                LoggerFactory.getLogger(RedisSnapshotListener.class).warn("Invalid SSE Redis envelope rejected");
            }
            }
        }
        container.addMessageListener(new Listener(), new ChannelTopic(channel));
        return container;
    }
    record Envelope(long fixtureId, String event, String data) {}
}
