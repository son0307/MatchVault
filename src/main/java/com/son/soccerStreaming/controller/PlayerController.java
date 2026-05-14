package com.son.soccerStreaming.controller;

import com.son.soccerStreaming.dto.PlayerResponseDto;
import com.son.soccerStreaming.service.PlayerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@Tag(name = "Player Information", description = "선수 정보, 통계 조회 API")
@RestController
@RequestMapping("/api/v1/players")
@RequiredArgsConstructor
public class PlayerController {

    private final PlayerService playerService;

    // 특정 선수 상세 정보 조회
    @Operation(summary = "선수 상세 정보 조회", description = "특정 선수의 정보를 조회합니다. (키, 몸무게, 등번호 등)")
    @GetMapping("/{playerId}")
    public ResponseEntity<PlayerResponseDto.Details> getPlayerDetails(@PathVariable Long playerId) {
        return ResponseEntity.ok(playerService.getPlayerDetails(playerId));
    }

    @Operation(summary = "선수 패널 조회", description = "선수 프로필, 시즌별 종합 스탯, 경기별 핵심 기록을 한 번에 조회합니다.")
    @GetMapping("/{playerId}/panel")
    public ResponseEntity<PlayerResponseDto.Panel> getPlayerPanel(@PathVariable Long playerId) {
        return ResponseEntity.ok(playerService.getPlayerPanel(playerId));
    }

    // 특정 선수 시즌 누적 스탯 조회
    @Operation(summary = "선수 누적 스탯 조회", description = "특정 선수의 누적 스탯을 조회합니다.")
    @GetMapping("/{playerId}/stats")
    public ResponseEntity<PlayerResponseDto.SeasonStats> getPlayerSeasonStats(
            @Parameter(description = "조회할 선수의 고유 ID (예: player_tottenham_6)")
            @PathVariable Long playerId) {
        return ResponseEntity.ok(playerService.getPlayerSeasonStats(playerId));
    }
}
