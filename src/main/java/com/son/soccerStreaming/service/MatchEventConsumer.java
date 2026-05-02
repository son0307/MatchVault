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
    private final MatchService matchService;
    private final MatchRedisService matchRedisService;
    private final SseService sseService;

    @KafkaListener(topics = "match-events", groupId = "soccer-streaming-group")
    public void consume(String messagePayload) {
        try {
            MatchEventDto event = objectMapper.readValue(messagePayload, MatchEventDto.class);
            Long fixtureId = event.getFixtureId();

            // 1. 프론트엔드가 '가장 최근 발생한 이벤트'를 띄워주기 위해 Redis에 저장
            // (예: "방금 전 토트넘 경고", "방금 전 득점")
            matchRedisService.saveLatestEvent(event);

            // 2. 득점 감지 및 즉시 DB(스코어) 업데이트
            if ("Goal".equals(event.getType())) {
                String detail = event.getDetail();

                if ("Normal Goal".equals(detail) || "Penalty".equals(detail)) {
                    Long teamId = event.getTeam().getId();
                    log.info("⚽ GOAL! [{}] 팀 득점! DB 스코어 업데이트 실행", teamId);

                    // 💡 타입 리팩토링 전이므로 String으로 변환해서 넘깁니다.
                    matchService.addScoreToDb(fixtureId, teamId);
                }
            }

            // 3. 접속 중인 유저들에게 SSE 실시간 알림 발송
            sseService.broadcastToMatch(String.valueOf(fixtureId), messagePayload);

            // 로깅용
            Integer elapsed = event.getTime() != null ? event.getTime().getElapsed() : 0;
            log.info("📡 수신된 라이브 이벤트: [{}분] {} ({})", elapsed, event.getType(), event.getDetail());

        } catch (JacksonException e) {
            log.error("잘못된 형식의 카프카 메시지 수신: {}", messagePayload);
        } catch (Exception e) {
            log.error("이벤트 처리 중 서버 오류 발생", e);
        }
    }
}