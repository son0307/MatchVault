package com.son.soccerStreaming.fixture.controller;

import com.son.soccerStreaming.fixture.dto.CursorResponse;
import com.son.soccerStreaming.fixture.dto.FixtureEventResponseDto;
import com.son.soccerStreaming.fixture.dto.FixtureLineupResponseDto;
import com.son.soccerStreaming.fixture.dto.FixtureMetaResponseDto;
import com.son.soccerStreaming.fixture.dto.FixturePlayerStatResponseDto;
import com.son.soccerStreaming.fixture.dto.FixtureResponseDto;
import com.son.soccerStreaming.fixture.dto.FixtureStatResponseDto;
import com.son.soccerStreaming.fixture.service.FixtureEventService;
import com.son.soccerStreaming.fixture.service.FixtureLineupService;
import com.son.soccerStreaming.fixture.service.FixturePlayerStatService;
import com.son.soccerStreaming.fixture.service.FixtureRedisService;
import com.son.soccerStreaming.fixture.service.FixtureService;
import com.son.soccerStreaming.fixture.service.FixtureStatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "경기 일정", description = "경기 일정 관련 API")
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

    @Operation(summary = "경기 일정 목록", description = "경기 일정을 커서 기반 페이지로 조회합니다.")
    @GetMapping
    public ResponseEntity<CursorResponse<FixtureResponseDto.Summary>> getFixtures(
            @Parameter(description = "이전 페이지의 마지막 경기 내부 ID", example = "105")
            @RequestParam(required = false) Long cursorId,
            @Parameter(description = "조회할 시즌", example = "2025")
            @RequestParam(required = false) Integer season,
            @Parameter(description = "라운드 번호", example = "38")
            @RequestParam(required = false) Integer round,
            @Parameter(description = "한국 시간 기준 단일 경기 날짜", example = "2026-05-15")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(description = "한국 시간 기준 조회 시작 날짜", example = "2026-05-15")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @Parameter(description = "한국 시간 기준 조회 종료 날짜", example = "2026-05-21")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @Parameter(description = "팀 API ID", example = "47")
            @RequestParam(required = false) Long teamId,
            @Parameter(description = "한 번에 가져올 경기 수", example = "10")
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(fixtureService.getRecentFixtures(
                cursorId, season, round, date, dateFrom, dateTo, teamId, size));
    }

    @Operation(summary = "경기 일정 메타", description = "시즌 내 경기 날짜와 라운드 범위를 조회합니다.")
    @GetMapping("/meta")
    public ResponseEntity<FixtureMetaResponseDto> getFixtureMeta(
            @Parameter(description = "조회할 시즌", example = "2025")
            @RequestParam(defaultValue = "2025") Integer season
    ) {
        return ResponseEntity.ok(fixtureService.getFixtureMeta(season));
    }

    @Operation(summary = "특정 경기 요약 조회", description = "경기 상세 화면 상단에 표시할 기본 경기 정보를 조회합니다.")
    @GetMapping("/{fixtureId}")
    public ResponseEntity<FixtureResponseDto.Summary> getFixture(
            @Parameter(description = "조회할 경기 API fixture ID", example = "1208000")
            @PathVariable Long fixtureId) {
        return ResponseEntity.ok(fixtureService.getFixture(fixtureId));
    }

    @Operation(summary = "Head-to-head fixtures", description = "Returns recent EPL fixtures between the two teams in the selected fixture.")
    @GetMapping("/{fixtureId}/head-to-head")
    public ResponseEntity<FixtureResponseDto.HeadToHead> getHeadToHead(
            @Parameter(description = "Fixture API ID", example = "1208000")
            @PathVariable Long fixtureId) {
        return ResponseEntity.ok(fixtureService.getHeadToHead(fixtureId));
    }

    @Operation(summary = "특정 경기 라인업 조회", description = "선발, 교체, 결장 선수 정보를 팀별로 조회합니다.")
    @GetMapping("/{fixtureId}/lineups")
    public ResponseEntity<FixtureLineupResponseDto.Lineup> getLineup(
            @Parameter(description = "조회할 경기 API fixture ID", example = "1208000")
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
            @Parameter(description = "조회할 경기 API fixture ID", example = "1208000")
            @PathVariable Long fixtureId) {
        return fixtureRedisService.getPlayerStats(fixtureId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.ok(fixturePlayerStatService.getFixturePlayerStats(fixtureId)));
    }

    @Operation(summary = "특정 경기 이벤트 조회", description = "특정 경기에서 발생한 골, 카드, 교체 등 이벤트 목록을 조회합니다.")
    @GetMapping("/{fixtureId}/events")
    public ResponseEntity<FixtureEventResponseDto> getFixtureEvents(
            @Parameter(description = "조회할 경기 API fixture ID", example = "1208000")
            @PathVariable Long fixtureId) {
        return ResponseEntity.ok(fixtureEventService.getFixtureEvents(fixtureId));
    }
}
