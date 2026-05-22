package com.son.soccerStreaming.live.controller;

import com.son.soccerStreaming.fixture.dto.FixturePlayerStatResponseDto;
import com.son.soccerStreaming.fixture.dto.FixtureResponseDto;
import com.son.soccerStreaming.fixture.dto.FixtureStatResponseDto;
import com.son.soccerStreaming.fixture.service.FixturePlayerStatService;
import com.son.soccerStreaming.fixture.service.FixtureRedisService;
import com.son.soccerStreaming.fixture.service.FixtureStatService;
import com.son.soccerStreaming.live.service.LiveFixtureQueryService;
import com.son.soccerStreaming.live.service.SseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Tag(name = "실시간 경기", description = "SSE 기반 실시간 경기 중계와 최신 경기 데이터 보강 조회 API")
@RestController
@RequestMapping("/api/v1/live")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class LiveFixtureController {

    private final SseService sseService;
    private final FixtureRedisService fixtureRedisService;
    private final FixtureStatService fixtureStatService;
    private final FixturePlayerStatService fixturePlayerStatService;
    private final LiveFixtureQueryService liveFixtureQueryService;

    @Operation(
            summary = "현재 라이브 경기 목록 조회",
            description = "현재 LIVE 상태인 경기 목록을 조회합니다."
    )
    @ApiResponse(
            responseCode = "200",
            description = "현재 라이브 중인 경기 목록",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
    )
    @GetMapping(value = "/fixtures", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<FixtureResponseDto.Summary>> getLiveFixtures(
            @Parameter(description = "조회할 시즌", example = "2025")
            @RequestParam(defaultValue = "2025") Integer season
    ) {
        return ResponseEntity.ok(liveFixtureQueryService.getTodayLiveFixtures(season));
    }

    // 클라이언트가 특정 fixture 방에 SSE로 접속하는 엔드포인트입니다.
    @Operation(
            summary = "실시간 경기 SSE 스트림 구독",
            description = """
                    특정 경기의 실시간 변경 사항을 받기 위한 Server-Sent Events 연결을 엽니다.

                    스트림은 `CONNECT` 이벤트로 시작하며, 스케줄러가 경기 데이터를 갱신할 때마다 실시간 업데이트를 전송합니다.
                    클라이언트는 `LIVE_SNAPSHOT`, `FIXTURE_EVENTS`, `PLAYER_STATS` 이벤트를 구독하면 됩니다.
                    각 이벤트 payload는 SSE `data` 필드에 JSON 문자열로 전달되므로 브라우저에서는 `event.data`를 JSON으로 파싱해야 합니다.

                    경기가 이미 진행 중인 상태에서 접속했다면, 먼저 실시간 팀 통계와 선수별 통계 보강 조회 API를 한 번 호출해 현재 화면을 채운 뒤
                    이후 변경 사항은 이 스트림을 통해 반영하는 방식을 권장합니다.
                    """
    )
    @ApiResponse(
            responseCode = "200",
            description = "SSE 스트림입니다. 클라이언트가 연결을 종료하거나 서버의 emitter timeout이 발생할 때까지 HTTP 연결이 유지됩니다.",
            content = @Content(
                    mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                    schema = @Schema(type = "string"),
                    examples = @ExampleObject(
                            name = "SSE events",
                            value = "event: CONNECT\n"
                                    + "data: 1208000: Successfully connected!\n\n"
                                    + "event: LIVE_SNAPSHOT\n"
                                    + "data: {\"fixtureId\":1208000,\"statusShort\":\"1H\",\"elapsed\":23,\"homeTeamStat\":{\"teamId\":33,\"score\":1},\"awayTeamStat\":{\"teamId\":40,\"score\":0}}\n\n"
                                    + "event: FIXTURE_EVENTS\n"
                                    + "data: {\"fixtureId\":1208000,\"events\":[{\"sequence\":1,\"type\":\"Goal\",\"detail\":\"Normal Goal\"}]}\n\n"
                                    + "event: PLAYER_STATS\n"
                                    + "data: {\"fixtureId\":1208000,\"homeTeam\":{\"teamId\":33,\"players\":[]},\"awayTeam\":{\"teamId\":40,\"players\":[]}}\n\n"
                    )
            )
    )
    @GetMapping(path = "/stream/fixtures/{fixtureId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @Parameter(description = "실시간 스트림을 구독할 fixture ID", example = "1208000")
            @PathVariable String fixtureId) {
        return sseService.subscribe(fixtureId);
    }

    // 중간 접속 사용자가 현재 경기 상태를 먼저 채울 수 있도록 최신 팀 통계를 제공합니다.
    @Operation(
            summary = "실시간 경기 팀 통계 스냅샷 조회",
            description = "실시간 경기의 최신 팀 통계 캐시를 조회합니다. 캐시된 실시간 스냅샷이 없으면 저장된 경기 통계를 반환합니다."
    )
    @ApiResponse(
            responseCode = "200",
            description = "해당 경기의 현재 팀 통계",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = FixtureStatResponseDto.class))
    )
    @GetMapping(value = "/fixtures/{fixtureId}/stats", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FixtureStatResponseDto> getFixtureStats(
            @Parameter(description = "실시간 팀 통계를 조회할 fixture ID", example = "1208000")
            @PathVariable Long fixtureId) {
        return fixtureRedisService.getLiveSnapshot(fixtureId)
                .map(snapshot -> ResponseEntity.ok(FixtureStatResponseDto.builder()
                        .fixtureId(snapshot.getFixtureId())
                        .homeTeamStat(snapshot.getHomeTeamStat())
                        .awayTeamStat(snapshot.getAwayTeamStat())
                        .build()))
                .orElseGet(() -> ResponseEntity.ok(fixtureStatService.getFixtureStats(fixtureId)));
    }

    // SSE 연결 전후로 선수별 최신 기록을 보강 조회할 수 있는 엔드포인트입니다.
    @Operation(
            summary = "실시간 경기 선수별 통계 스냅샷 조회",
            description = "실시간 경기의 최신 선수별 통계 캐시를 조회합니다. 캐시된 실시간 선수 통계가 없으면 저장된 경기 선수 통계를 반환합니다."
    )
    @ApiResponse(
            responseCode = "200",
            description = "해당 경기 양 팀의 현재 선수별 통계",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = FixturePlayerStatResponseDto.class))
    )
    @GetMapping(value = "/fixtures/{fixtureId}/player-stats", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FixturePlayerStatResponseDto> getFixturePlayerStats(
            @Parameter(description = "실시간 선수별 통계를 조회할 fixture ID", example = "1208000")
            @PathVariable Long fixtureId) {
        return fixtureRedisService.getPlayerStats(fixtureId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.ok(fixturePlayerStatService.getFixturePlayerStats(fixtureId)));
    }
}
