package com.son.soccerStreaming.service;

import com.son.soccerStreaming.dto.MatchEventDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

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
    public void incrementEventCount(String matchId, String eventType, String teamId) {
        String key = "match:" + matchId + ":count:" + eventType + ":" + teamId;
        redisTemplate.opsForValue().increment(key);
    }

    // 선수별 스탯 누적
    public void updatePlayerStat(MatchEventDto event) {
        String matchId = event.getMatchId();
        String playerId = event.getPlayerId();
        String eventType = event.getEventType();
        Map<String, Object> detail = event.getEventDetail();

        if (playerId == null || "OVER".equals(eventType)) return;

        String key = "match:" + matchId + ":player:" + playerId;
        HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();

        switch (eventType) {
            case "PASS":
                hashOps.increment(key, "totalPasses", 1);
                // 성공한 패스
                if (detail != null && (Boolean) detail.getOrDefault("is_successful", false)) {
                    hashOps.increment(key, "successfulPasses", 1);
                }
                break;
            case "SHOT":
                hashOps.increment(key, "totalShots", 1);
                // 유효 슈팅이 된 슈팅
                if (detail != null && (Boolean) detail.getOrDefault("is_on_target", false)) {
                    hashOps.increment(key, "shotsOnTarget", 1);
                    // 골로 연결된 경우
                    if ((Boolean) detail.getOrDefault("is_goal", false)) {
                        hashOps.increment(key, "goals", 1);
                        incrementEventCount(matchId, "GOAL", event.getTeamId());
                    }
                }
                break;
            case "TACKLE":
                hashOps.increment(key, "tackles", 1);
                break;
            case "FOUL":
                hashOps.increment(key, "fouls", 1);
                break;
        }
    }
}
