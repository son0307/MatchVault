package com.son.soccerStreaming.service;

import com.son.soccerStreaming.dto.MatchEventDto;
import com.son.soccerStreaming.dto.MatchStatResponseDto;
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

    // 선수별, 팀별 스탯 누적
    public void updateStat(MatchEventDto event) {
        String matchId = event.getMatchId();
        String playerId = event.getPlayerId();
        String teamId = event.getTeamId();
        String eventType = event.getEventType();
        Map<String, Object> detail = event.getEventDetail();

        if (playerId == null || "OVER".equals(eventType)) return;

        String playerKey = "match:" + matchId + ":player:" + playerId;
        String teamKey = "match:" + matchId+ ":team:" + teamId;
        HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();

        switch (eventType) {
            case "PASS":
                hashOps.increment(playerKey, "totalPasses", 1);
                hashOps.increment(teamKey, "totalPasses", 1);
                // 성공한 패스
                if (detail != null && (Boolean) detail.getOrDefault("is_successful", false)) {
                    hashOps.increment(playerKey, "successfulPasses", 1);
                    hashOps.increment(teamKey, "successfulPasses", 1);
                }
                break;
            case "SHOT":
                hashOps.increment(playerKey, "totalShots", 1);
                hashOps.increment(teamKey, "totalShots", 1);
                // 유효 슈팅이 된 슈팅
                if (detail != null && (Boolean) detail.getOrDefault("is_on_target", false)) {
                    hashOps.increment(playerKey, "shotsOnTarget", 1);
                    hashOps.increment(teamKey, "shotsOnTarget", 1);
                    // 골로 연결된 경우
                    if ((Boolean) detail.getOrDefault("is_goal", false)) {
                        hashOps.increment(playerKey, "goals", 1);
                        hashOps.increment(teamKey, "goals", 1);
                    }
                }
                break;
            case "TACKLE":
                hashOps.increment(playerKey, "tackles", 1);
                hashOps.increment(teamKey, "tackles", 1);
                break;
            case "FOUL":
                hashOps.increment(playerKey, "fouls", 1);
                hashOps.increment(teamKey, "fouls", 1);
                break;
        }
    }

    public MatchStatResponseDto.TeamStatSummary getTeamStatSummary(String matchId, String teamId) {
        String key = "match:" + matchId + ":team:" + teamId;
        var entries = redisTemplate.opsForHash().entries(key);

        int passes = parseStat(entries.get("totalPasses"));
        int successfulPasses = parseStat(entries.get("successfulPasses"));
        double passAccuracy = passes != 0 ? (double) (successfulPasses * 100) / passes : 0;

        return MatchStatResponseDto.TeamStatSummary.builder()
                .teamId(teamId)
                .score(parseStat(entries.get("goals")))
                .totalShots(parseStat(entries.get("totalShots")))
                .shotsOnTarget(parseStat(entries.get("shotsOnTarget")))
                .totalPasses(passes)
                .successfulPasses(successfulPasses)
                .passAccuracy(passAccuracy)
                .fouls(parseStat(entries.get("fouls")))
                .tackles(parseStat(entries.get("tackles")))
                .build();
    }

    private int parseStat(Object value) {
        return value == null ? 0 : Integer.parseInt(value.toString());
    }
}
