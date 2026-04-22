package com.son.soccerStreaming.controller;

import com.son.soccerStreaming.dto.CursorResponse;
import com.son.soccerStreaming.dto.MatchLineupResponseDto;
import com.son.soccerStreaming.dto.MatchResponseDto;
import com.son.soccerStreaming.dto.MatchStatResponseDto;
import com.son.soccerStreaming.service.MatchLineupService;
import com.son.soccerStreaming.service.MatchService;
import com.son.soccerStreaming.service.MatchStatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/matches")
@RequiredArgsConstructor
public class MatchController {

    private final MatchLineupService matchLineupService;
    private final MatchStatService matchStatService;
    private final MatchService matchService;

    // 최근 경기 목록 조회 (커서 기반 페이징 적용)
    @GetMapping
    public ResponseEntity<CursorResponse<MatchResponseDto.Summary>> getMatches(
            @RequestParam(required = false) Long cursorId,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(matchService.getMatches(cursorId, size));
    }

    // 경기 선발 라인업 조회
    @GetMapping("/{matchId}/lineups")
    public ResponseEntity<MatchLineupResponseDto.Lineup> getLineup(@PathVariable String matchId) {
        return ResponseEntity.ok(matchLineupService.getMatchLineups(matchId));
    }

    // 특정 경기 통계 조회
    @GetMapping("/{matchId}/stats")
    public ResponseEntity<MatchStatResponseDto> getMatchStats(@PathVariable String matchId) {
        return ResponseEntity.ok(matchStatService.getMatchStats(matchId));
    }
}
