package com.son.soccerStreaming.controller;

import com.son.soccerStreaming.dto.MatchStatResponseDto;
import com.son.soccerStreaming.service.MatchRedisService;
import com.son.soccerStreaming.service.MatchStatService;
import com.son.soccerStreaming.service.SseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/live")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")     // 테스트를 위해 CORS 모두 허용
public class LiveMatchController {

    private final SseService sseService;
    private final MatchRedisService matchRedisService;
    private final MatchStatService matchStatService;

    // 클라이언트가 SSE 연결을 맺는 엔드포인트
    @GetMapping(path = "/stream/matches/{matchId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String matchId) {
        return sseService.subscribe(matchId);
    }

    // 중간에 접속한 유저를 위한 실시간 정보 스냅샷 제공
    @GetMapping("/matches/{matchId}/stats")
    public ResponseEntity<MatchStatResponseDto> getMatchStats(
            @PathVariable Long matchId
    ) {
        return matchRedisService.getLiveSnapshot(matchId)
                .map(snapshot -> ResponseEntity.ok(MatchStatResponseDto.builder()
                        .matchId(snapshot.getFixtureId())
                        .homeTeamStat(snapshot.getHomeTeamStat())
                        .awayTeamStat(snapshot.getAwayTeamStat())
                        .build()))
                .orElseGet(() -> ResponseEntity.ok(matchStatService.getMatchStats(matchId)));
    }
}
