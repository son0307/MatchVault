package com.son.soccerStreaming.controller;

import com.son.soccerStreaming.dto.CursorResponse;
import com.son.soccerStreaming.dto.MatchEventResponseDto;
import com.son.soccerStreaming.dto.MatchLineupResponseDto;
import com.son.soccerStreaming.dto.MatchPlayerStatResponseDto;
import com.son.soccerStreaming.dto.MatchResponseDto;
import com.son.soccerStreaming.dto.MatchStatResponseDto;
import com.son.soccerStreaming.service.MatchEventService;
import com.son.soccerStreaming.service.MatchLineupService;
import com.son.soccerStreaming.service.MatchPlayerStatService;
import com.son.soccerStreaming.service.MatchService;
import com.son.soccerStreaming.service.MatchStatService;
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

@Tag(name = "Matches", description = "Match information API")
@RestController
@RequestMapping("/api/v1/matches")
@RequiredArgsConstructor
public class MatchController {

    private final MatchLineupService matchLineupService;
    private final MatchStatService matchStatService;
    private final MatchPlayerStatService matchPlayerStatService;
    private final MatchEventService matchEventService;
    private final MatchService matchService;

    @Operation(summary = "최근 경기 목록 조회", description = "커서 기반 페이지네이션으로 최근 경기 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<CursorResponse<MatchResponseDto.Summary>> getMatches(
            @Parameter(description = "이전 페이지의 마지막 경기 내부 ID", example = "105")
            @RequestParam(required = false) Long cursorId,
            @Parameter(description = "한 번에 가져올 경기 수", example = "10")
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(matchService.getRecentMatches(cursorId, size));
    }

    @Operation(summary = "특정 경기 라인업 조회", description = "선발, 교체, 결장 선수 정보를 팀별로 조회합니다.")
    @GetMapping("/{matchId}/lineups")
    public ResponseEntity<MatchLineupResponseDto.Lineup> getLineup(
            @Parameter(description = "조회할 경기의 API fixture ID", example = "1208000")
            @PathVariable Long matchId
    ) {
        return ResponseEntity.ok(matchLineupService.getMatchLineups(matchId));
    }

    @Operation(summary = "특정 경기 팀 스탯 조회", description = "특정 경기의 팀별 집계 스탯을 조회합니다.")
    @GetMapping("/{matchId}/stats")
    public ResponseEntity<MatchStatResponseDto> getMatchStats(
            @Parameter(description = "조회할 경기의 API fixture ID", example = "1208000")
            @PathVariable Long matchId
    ) {
        return ResponseEntity.ok(matchStatService.getMatchStats(matchId));
    }

    @Operation(summary = "특정 경기 선수별 스탯 조회", description = "특정 경기의 모든 선수 스탯을 팀별로 조회합니다.")
    @GetMapping("/{matchId}/player-stats")
    public ResponseEntity<MatchPlayerStatResponseDto> getMatchPlayerStats(
            @Parameter(description = "조회할 경기의 API fixture ID", example = "1208000")
            @PathVariable Long matchId
    ) {
        return ResponseEntity.ok(matchPlayerStatService.getPlayerStats(matchId));
    }

    @Operation(summary = "특정 경기 이벤트 조회", description = "특정 경기에서 발생한 골, 카드, 교체 등 이벤트 목록을 조회합니다.")
    @GetMapping("/{matchId}/events")
    public ResponseEntity<MatchEventResponseDto> getMatchEvents(
            @Parameter(description = "조회할 경기의 API fixture ID", example = "1208000")
            @PathVariable Long matchId
    ) {
        return ResponseEntity.ok(matchEventService.getMatchEvents(matchId));
    }
}
