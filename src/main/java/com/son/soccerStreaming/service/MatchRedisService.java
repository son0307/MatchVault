package com.son.soccerStreaming.service;

import com.son.soccerStreaming.dto.MatchEventDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchRedisService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // 최신 이벤트 데이터 업데이트
    public void saveLatestEvent(MatchEventDto event) {
        try {
            String eventJson = objectMapper.writeValueAsString(event);
            redisTemplate.opsForValue().set("match:latest_event", eventJson);
        } catch (JacksonException e) {
            log.error("Redis 저장 중 JSON 변환 오류", e);
        }
    }

    // 이벤트 타입별 카운트 누적
    public void incrementEventCount(String eventType, String teamId) {
        String key = "match:count:" + eventType + ":" + teamId;
        redisTemplate.opsForValue().increment(key);
    }
}
