package com.son.soccerStreaming.player.controller;

import com.son.soccerStreaming.player.dto.PlayerResponseDto;
import com.son.soccerStreaming.player.service.PlayerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "선수 정보", description = "선수 프로필과 패널 조회 API")
@RestController
@RequestMapping("/api/v1/players")
@RequiredArgsConstructor
public class PlayerController {

    private final PlayerService playerService;

    @Operation(summary = "선수 상세 정보 조회", description = "특정 선수의 프로필 상세 정보를 조회합니다.")
    @GetMapping("/{playerId}")
    public ResponseEntity<PlayerResponseDto.Details> getPlayerDetails(@PathVariable Long playerId) {
        return ResponseEntity.ok(playerService.getPlayerDetails(playerId));
    }

    @Operation(summary = "선수 패널 조회", description = "선수 프로필, 시즌별 요약 기록, 경기별 핵심 기록을 한 번에 조회합니다.")
    @GetMapping("/{playerId}/panel")
    public ResponseEntity<PlayerResponseDto.Panel> getPlayerPanel(@PathVariable Long playerId) {
        return ResponseEntity.ok(playerService.getPlayerPanel(playerId));
    }
}
