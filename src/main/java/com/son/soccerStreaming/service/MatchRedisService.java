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

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchRedisService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // 최신 이벤트 데이터 업데이트 (프론트엔드 알림용)
    public void saveLatestEvent(MatchEventDto event) {
        try {
            String eventJson = objectMapper.writeValueAsString(event);
            redisTemplate.opsForValue().set("match:latest_event", eventJson);
        } catch (JacksonException e) {
            log.error("Redis 저장 중 JSON 변환 오류", e);
        }
    }

    // 💡 API-Sports 규격에 맞춘 선수별/팀별 실시간 스탯 누적 (Goal, Card 위주)
    public void updateEventStat(MatchEventDto event) {
        Long fixtureId = event.getFixtureId();

        // Null 방지 처리 (VAR 이벤트 등에서는 선수가 없을 수도 있음)
        Long teamId = event.getTeam() != null ? event.getTeam().getId() : null;
        Long playerId = event.getPlayer() != null ? event.getPlayer().getId() : null;
        String eventType = event.getType();
        String detail = event.getDetail();

        if (fixtureId == null || teamId == null || eventType == null) return;

        HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
        String teamKey = "match:" + fixtureId + ":team:" + teamId;
        String playerKey = playerId != null ? "match:" + fixtureId + ":player:" + playerId : null;

        switch (eventType) {
            case "Goal":
                // 일반 필드골과 페널티킥만 득점으로 인정 (자책골 제외)
                if ("Normal Goal".equals(detail) || "Penalty".equals(detail)) {
                    hashOps.increment(teamKey, "goals", 1);
                    if (playerKey != null) hashOps.increment(playerKey, "goals", 1);
                }
                break;

            case "Card":
                if ("Yellow Card".equals(detail)) {
                    hashOps.increment(teamKey, "yellowCards", 1);
                    if (playerKey != null) hashOps.increment(playerKey, "yellowCards", 1);
                } else if ("Red card".equals(detail) || "Red Card".equals(detail)) {
                    hashOps.increment(teamKey, "redCards", 1);
                    if (playerKey != null) hashOps.increment(playerKey, "redCards", 1);
                }
                break;
        }
    }

    // 💡 파라미터 타입을 String -> Long으로 통일하고, 저장되는 데이터 규격에 맞춰 반환값 수정
    public MatchStatResponseDto.TeamStatSummary getTeamStatSummary(Long fixtureId, Long teamId) {
        String key = "match:" + fixtureId + ":team:" + teamId;
        var entries = redisTemplate.opsForHash().entries(key);

        return MatchStatResponseDto.TeamStatSummary.builder()
                .teamId(teamId)
                .score(parseStat(entries.get("goals")))
                .yellowCards(parseStat(entries.get("yellowCards")))
                .redCards(parseStat(entries.get("redCards")))
                .build();
    }

    private int parseStat(Object value) {
        return value == null ? 0 : Integer.parseInt(value.toString());
    }
}