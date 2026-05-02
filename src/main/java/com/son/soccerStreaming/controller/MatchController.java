package com.son.soccerStreaming.controller;

import com.son.soccerStreaming.dto.CursorResponse;
import com.son.soccerStreaming.dto.MatchLineupResponseDto;
import com.son.soccerStreaming.dto.MatchResponseDto;
import com.son.soccerStreaming.dto.MatchStatResponseDto;
import com.son.soccerStreaming.service.MatchLineupService;
import com.son.soccerStreaming.service.MatchService;
import com.son.soccerStreaming.service.MatchStatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Matches", description = "경기 정보, 라인업 및 기록 조회 API")
@RestController
@RequestMapping("/api/v1/matches")
@RequiredArgsConstructor
public class MatchController {

    private final MatchLineupService matchLineupService;
    private final MatchStatService matchStatService;
    private final MatchService matchService;

    // 최근 경기 목록 조회 (커서 기반 페이징 적용)
    @Operation(summary = "최근 경기 목록 조회", description = "커서 기반 페이징을 적용하여 최근 진행된 경기 목록을 조회합니다")
    @GetMapping
    public ResponseEntity<CursorResponse<MatchResponseDto.Summary>> getMatches(
            @Parameter(description = "이전 페이지의 마지막 경기 ID (첫 페이지 조회 시 생략)", example = "105")
            @RequestParam(required = false) Long cursorId,

            @Parameter(description = "한 번에 가져올 경기 데이터 개수", example = "10")
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(matchService.getRecentMatches(cursorId, size));
    }

    // 경기 선발 라인업 조회
    @Operation(summary = "경기 선발 라인업 조회", description = "특정 경기에 출전하는 홈팀과 원정팀의 선발 및 교체 선수 명단을 조회합니다.")
    @GetMapping("/{matchId}/lineups")
    public ResponseEntity<MatchLineupResponseDto.Lineup> getLineup(
            @Parameter(description = "조회할 경기의 고유 ID", example = "match_01")
            @PathVariable Long matchId) {
        return ResponseEntity.ok(matchLineupService.getMatchLineups(matchId));
    }

    // 특정 경기 통계 조회
    @Operation(summary = "특정 경기 통계 조회", description = "종료된 특정 경기에서 발생한 양 팀의 상세 스탯(점유율, 슈팅, 패스 등)을 조회합니다.")
    @GetMapping("/{matchId}/stats")
    public ResponseEntity<MatchStatResponseDto> getMatchStats(
            @Parameter(description = "조회할 경기의 고유 ID", example = "match_01")
            @PathVariable Long matchId) {
        return ResponseEntity.ok(matchStatService.getMatchStats(matchId));
    }
}
