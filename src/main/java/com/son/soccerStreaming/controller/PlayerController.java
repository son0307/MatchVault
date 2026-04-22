package com.son.soccerStreaming.controller;

import com.son.soccerStreaming.dto.PlayerResponseDto;
import com.son.soccerStreaming.service.PlayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/players")
@RequiredArgsConstructor
public class PlayerController {

    private final PlayerService playerService;

    // 특정 선수 상세 정보 조회
    @GetMapping("/{playerId}")
    public ResponseEntity<PlayerResponseDto.Details> getPlayerDetails(@PathVariable String playerId) {
        return ResponseEntity.ok(playerService.getPlayerDetails(playerId));
    }

    // 특정 선수 시즌 누적 스탯 조회
    @GetMapping("/{playerId}/stats")
    public ResponseEntity<PlayerResponseDto.SeasonStats> getPlayerSeasonStats(@PathVariable String playerId) {
        return ResponseEntity.ok(playerService.getPlayerSeasonStats(playerId));
    }
}
