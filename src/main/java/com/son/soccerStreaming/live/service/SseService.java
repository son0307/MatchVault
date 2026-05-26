package com.son.soccerStreaming.live.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SseService {

    private final Map<Long, Set<SseEmitter>> fixtureEmitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long fixtureId) {
        SseEmitter emitter = new SseEmitter(1000 * 60 * 5L);

        // 해당 fixtureId의 방이 있으면 합류, 없으면 새로 생성
        Set<SseEmitter> room = fixtureEmitters.computeIfAbsent(fixtureId, key -> ConcurrentHashMap.newKeySet());
        room.add(emitter);

        // 연결 종료, 타임 아웃 -> 목록에서 제거
        emitter.onCompletion(() -> removeEmitter(fixtureId, room, emitter));
        emitter.onTimeout(() -> removeEmitter(fixtureId, room, emitter));
        emitter.onError(error -> removeEmitter(fixtureId, room, emitter));

        try {
            // 첫 연결 시 더미 데이터 전송 (503 에러 방지)
            emitter.send(SseEmitter.event().name("CONNECT").data(fixtureId + ": Successfully connected!"));
        } catch (IOException e) {
            removeEmitter(fixtureId, room, emitter);
        }

        return emitter;
    }

    // The fast live poller follows only fixtures with active SSE subscribers.
    public List<Long> getSubscribedFixtureIds() {
        return fixtureEmitters.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .toList();
    }

    // Broadcast live fixture updates to connected clients.
    public void broadcastToFixture(Long fixtureId, String jsonMessage) {
        broadcastToFixture(fixtureId, "FIXTURE_EVENT", jsonMessage);
    }

    public void broadcastToFixture(Long fixtureId, String eventName, String jsonMessage) {
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
                removeEmitter(fixtureId, room, emitter);
            }
        }
    }

    private void removeEmitter(Long fixtureId, Set<SseEmitter> room, SseEmitter emitter) {
        room.remove(emitter);
        if (room.isEmpty()) {
            fixtureEmitters.remove(fixtureId, room);
        }
    }

}
