package com.son.soccerStreaming.controller;

import com.son.soccerStreaming.dto.FixturePlayerStatResponseDto;
import com.son.soccerStreaming.dto.FixtureStatResponseDto;
import com.son.soccerStreaming.service.FixtureRedisService;
import com.son.soccerStreaming.service.FixturePlayerStatService;
import com.son.soccerStreaming.service.FixtureStatService;
import com.son.soccerStreaming.service.SseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/live")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class LiveFixtureController {

    private final SseService sseService;
    private final FixtureRedisService fixtureRedisService;
    private final FixtureStatService fixtureStatService;
    private final FixturePlayerStatService fixturePlayerStatService;

    // 클라이언트가 SSE 연결을 맺는 엔드포인트
    @GetMapping(path = "/stream/fixtures/{fixtureId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String fixtureId) {
        return sseService.subscribe(fixtureId);
    }

    // 중간에 접속한 유저를 위한 실시간 정보 스냅샷 제공
    @GetMapping("/fixtures/{fixtureId}/stats")
    public ResponseEntity<FixtureStatResponseDto> getFixtureStats(@PathVariable Long fixtureId) {
        return fixtureRedisService.getLiveSnapshot(fixtureId)
                .map(snapshot -> ResponseEntity.ok(FixtureStatResponseDto.builder()
                        .fixtureId(snapshot.getFixtureId())
                        .homeTeamStat(snapshot.getHomeTeamStat())
                        .awayTeamStat(snapshot.getAwayTeamStat())
                        .build()))
                .orElseGet(() -> ResponseEntity.ok(fixtureStatService.getFixtureStats(fixtureId)));
    }

    @GetMapping("/fixtures/{fixtureId}/player-stats")
    public ResponseEntity<FixturePlayerStatResponseDto> getFixturePlayerStats(@PathVariable Long fixtureId) {
        return fixtureRedisService.getPlayerStats(fixtureId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.ok(fixturePlayerStatService.getFixturePlayerStats(fixtureId)));
    }
}
