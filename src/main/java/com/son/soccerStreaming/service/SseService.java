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

    private final Map<String, Set<SseEmitter>> matchEmitters = new ConcurrentHashMap<>();

    // 클라이언트가 새로 접속
    public SseEmitter subscribe(String matchId) {
        // 타임아웃 5분 설정
        SseEmitter emitter = new SseEmitter(1000 * 60 * 5L);

        // 해당 matchId의 방이 있으면 합류, 없으면 새로 생성
        Set<SseEmitter> room = matchEmitters.computeIfAbsent(matchId, k -> ConcurrentHashMap.newKeySet());
        room.add(emitter);

        // 연결 종료, 타임 아웃 -> 목록에서 제거
        emitter.onCompletion(() -> room.remove(emitter));
        emitter.onTimeout(() -> room.remove(emitter));
        emitter.onError((e) -> room.remove(emitter));

        try {
            // 첫 연결 시 더미 데이터 전송 (503 에러 방지)
            emitter.send(SseEmitter.event().name("CONNECT").data(matchId + ": Successfully connected!"));
        } catch (IOException e) {
            room.remove(emitter);
        }

        return emitter;
    }

    // Kafka로부터 새 이벤트 수신 -> 클라이언트들에게 전송
    public void broadcastToMatch(String matchId, String jsonMessage) {
        Set<SseEmitter> room = matchEmitters.get(matchId);

        if (room != null && !room.isEmpty()) {
            for (SseEmitter emitter : room) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("MATCH_EVENT")
                            .data(jsonMessage));
                } catch (IOException e) {
                    // 전송 실패한 파이프는 죽은 클라이언트이므로 제거
                    room.remove(emitter);
                }
            }
        }
    }
}
