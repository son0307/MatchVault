package com.son.soccerStreaming.service;

import com.son.soccerStreaming.dto.FixtureEventDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class FixtureEventConsumer {

    private final ObjectMapper objectMapper;
    private final FixtureRedisService fixtureRedisService;
    private final SseService sseService;

    @KafkaListener(topics = "fixture-events", groupId = "soccer-streaming-group")
    public void consume(String messagePayload) {
        try {
            FixtureEventDto event = objectMapper.readValue(messagePayload, FixtureEventDto.class);
            Long fixtureId = event.getFixtureId();

            fixtureRedisService.saveLatestEvent(event);
            sseService.broadcastToFixture(String.valueOf(fixtureId), messagePayload);

            Integer elapsed = event.getTime() != null ? event.getTime().getElapsed() : 0;
            log.info("📡 수신된 라이브 이벤트: [{}분] {} ({})", elapsed, event.getType(), event.getDetail());

        } catch (JacksonException e) {
            log.error("잘못된 형식의 카프카 메시지 수신: {}", messagePayload);
        } catch (Exception e) {
            log.error("이벤트 처리 중 서버 오류 발생", e);
        }
    }
}