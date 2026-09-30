package com.son.soccerStreaming.live.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "live.sse.transport", havingValue = "redis")
public class RedisSsePublisher {
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final String channel;
    public RedisSsePublisher(StringRedisTemplate redis, ObjectMapper mapper,
                             @Value("${live.sse.redis-channel:soccer:sse:v1}") String channel) {
        this.redis = redis; this.mapper = mapper; this.channel = channel;
    }
    public void publish(long fixtureId, String event, String json) {
        // A Redis failure is surfaced to the caller, never reported as successful delivery.
        redis.convertAndSend(channel, mapper.writeValueAsString(new Envelope(fixtureId, event, json)));
    }
    public record Envelope(long fixtureId, String event, String data) {}
}
