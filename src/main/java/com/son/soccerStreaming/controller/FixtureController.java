package com.son.soccerStreaming.controller;

import com.son.soccerStreaming.dto.CursorResponse;
import com.son.soccerStreaming.dto.FixtureEventResponseDto;
import com.son.soccerStreaming.dto.FixtureLineupResponseDto;
import com.son.soccerStreaming.dto.FixturePlayerStatResponseDto;
import com.son.soccerStreaming.dto.FixtureResponseDto;
import com.son.soccerStreaming.dto.FixtureStatResponseDto;
import com.son.soccerStreaming.service.FixtureEventService;
import com.son.soccerStreaming.service.FixtureLineupService;
import com.son.soccerStreaming.service.FixturePlayerStatService;
import com.son.soccerStreaming.service.FixtureRedisService;
import com.son.soccerStreaming.service.FixtureService;
import com.son.soccerStreaming.service.FixtureStatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Fixtures", description = "Fixture information API")
@RestController
@RequestMapping("/api/v1/fixtures")
@RequiredArgsConstructor
public class FixtureController {

    private final FixtureLineupService fixtureLineupService;
    private final FixtureStatService fixtureStatService;
    private final FixturePlayerStatService fixturePlayerStatService;
    private final FixtureEventService fixtureEventService;
    private final FixtureService fixtureService;
    private final FixtureRedisService fixtureRedisService;

    @Operation(summary = "List recent fixtures", description = "Returns fixtures with cursor-based pagination.")
    @GetMapping
    public ResponseEntity<CursorResponse<FixtureResponseDto.Summary>> getFixtures(
            @Parameter(description = "이전 페이지의 마지막 경기 내부 ID", example = "105")
            @RequestParam(required = false) Long cursorId,
            @Parameter(description = "조회할 시즌", example = "2024")
            @RequestParam(required = false) Integer season,
            @Parameter(description = "한 번에 가져올 경기 수", example = "10")
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(fixtureService.getRecentFixtures(cursorId, season, size));
    }

    @Operation(summary = "특정 경기 라인업 조회", description = "선발, 교체, 결장 선수 정보를 팀별로 조회합니다.")
    @GetMapping("/{fixtureId}/lineups")
    public ResponseEntity<FixtureLineupResponseDto.Lineup> getLineup(
            @Parameter(description = "조회할 경기의 API fixture ID", example = "1208000")
            @PathVariable Long fixtureId) {
        return ResponseEntity.ok(fixtureLineupService.getFixtureLineups(fixtureId));
    }

    @Operation(summary = "특정 경기 팀 스탯 조회", description = "특정 경기의 팀별 집계 스탯을 조회합니다.")
    @GetMapping("/{fixtureId}/stats")
    public ResponseEntity<FixtureStatResponseDto> getFixtureStats(@PathVariable Long fixtureId) {
        return fixtureRedisService.getLiveSnapshot(fixtureId)
                .map(snapshot -> ResponseEntity.ok(FixtureStatResponseDto.builder()
                        .fixtureId(snapshot.getFixtureId())
                        .homeTeamStat(snapshot.getHomeTeamStat())
                        .awayTeamStat(snapshot.getAwayTeamStat())
                        .build()))
                .orElseGet(() -> ResponseEntity.ok(fixtureStatService.getFixtureStats(fixtureId)));
    }

    @Operation(summary = "특정 경기 선수별 스탯 조회", description = "특정 경기의 모든 선수 스탯을 팀별로 조회합니다.")
    @GetMapping("/{fixtureId}/player-stats")
    public ResponseEntity<FixturePlayerStatResponseDto> getFixturePlayerStats(
            @Parameter(description = "조회할 경기의 API fixture ID", example = "1208000")
            @PathVariable Long fixtureId) {
        return fixtureRedisService.getPlayerStats(fixtureId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.ok(fixturePlayerStatService.getFixturePlayerStats(fixtureId)));
    }

    @Operation(summary = "특정 경기 이벤트 조회", description = "특정 경기에서 발생한 골, 카드, 교체 등 이벤트 목록을 조회합니다.")
    @GetMapping("/{fixtureId}/events")
    public ResponseEntity<FixtureEventResponseDto> getFixtureEvents(
            @Parameter(description = "조회할 경기의 API fixture ID", example = "1208000")
            @PathVariable Long fixtureId) {
        return ResponseEntity.ok(fixtureEventService.getFixtureEvents(fixtureId));
    }
}
