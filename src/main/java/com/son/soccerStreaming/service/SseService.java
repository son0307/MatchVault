package com.son.soccerStreaming.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SseService {

    private final Map<String, Set<SseEmitter>> fixtureEmitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String fixtureId) {
        SseEmitter emitter = new SseEmitter(1000 * 60 * 5L);

        // 해당 fixtureId의 방이 있으면 합류, 없으면 새로 생성
        Set<SseEmitter> room = fixtureEmitters.computeIfAbsent(fixtureId, key -> ConcurrentHashMap.newKeySet());
        room.add(emitter);

        // 연결 종료, 타임 아웃 -> 목록에서 제거
        emitter.onCompletion(() -> room.remove(emitter));
        emitter.onTimeout(() -> room.remove(emitter));
        emitter.onError(error -> room.remove(emitter));

        try {
            // 첫 연결 시 더미 데이터 전송 (503 에러 방지)
            emitter.send(SseEmitter.event().name("CONNECT").data(fixtureId + ": Successfully connected!"));
        } catch (IOException e) {
            room.remove(emitter);
        }

        return emitter;
    }

    // Kafka로부터 새 이벤트 수신 -> 클라이언트들에게 전송
    public void broadcastToFixture(String fixtureId, String jsonMessage) {
        broadcastToFixture(fixtureId, "FIXTURE_EVENT", jsonMessage);
    }

    public void broadcastToFixture(String fixtureId, String eventName, String jsonMessage) {
        Set<SseEmitter> room = fixtureEmitters.get(fixtureId);

        if (room == null || room.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : room) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(jsonMessage));
            } catch (IOException e) {
                // 전송 실패한 파이프는 죽은 클라이언트이므로 제거
                room.remove(emitter);
            }
        }
    }
}
