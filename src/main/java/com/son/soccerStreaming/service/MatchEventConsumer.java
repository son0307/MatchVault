package com.son.soccerStreaming.service;

import com.son.soccerStreaming.dto.MatchEventDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchEventConsumer {

    private final ObjectMapper objectMapper;
    private final MatchRedisService matchRedisService;
    private final MatchResultService matchResultService;
    private final SseService sseService;

    @KafkaListener(topics = "match-events", groupId = "soccer-streaming-group")
    public void consume(String messagePayload) {
        try {
            MatchEventDto event = objectMapper.readValue(messagePayload, MatchEventDto.class);

            if ("OVER".equals(event.getEventType())) {
                log.info("[{}] 경기 종료! DB 업데이트 시작.", event.getMatchId());
                matchResultService.closeMatch(event.getMatchId());
                sseService.broadcastToMatch(event.getMatchId(), messagePayload);
                return;
            }

            // Redis에 최신 상태 저장 및 팀별 통계 업데이트
            matchRedisService.saveLatestEvent(event);
            matchRedisService.incrementEventCount(event.getMatchId(), event.getEventType(), event.getTeamId());

            // 선수별 상세 스탯 업데이트
            matchRedisService.updatePlayerStat(event);

            // 클라이언트로 데이터 전송
            sseService.broadcastToMatch(event.getMatchId(), messagePayload);

            log.info("수신된 이벤트 : [{}분] {} - 선수 ID: {}",
                    event.getMatchMinute(),
                    event.getEventType(),
                    event.getPlayerId());

            processEvent(event);

        } catch (JacksonException e) {
            log.error("잘못된 형식의 데이터 수신. 내용: {}", messagePayload);
        }
    }

    private void processEvent(MatchEventDto event) {
        if("GOAL".equals(event.getEventType())) {
            log.info("GOAL! 스코어 업데이트 로직 실행");
        } else if ("OVER".equals(event.getEventType())) {

            matchResultService.closeMatch(event.getMatchId());
        }
    }
}
