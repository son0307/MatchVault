package com.son.soccerStreaming.controller;

import com.son.soccerStreaming.dto.PlayerResponseDto;
import com.son.soccerStreaming.service.PlayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PlayerController {

    private final PlayerService playerService;

    // 특정 팀 소속 선수 조회
    @GetMapping("/teams/{teamId}/players")
    public List<PlayerResponseDto.Summary> getTeamPlayers(@PathVariable String teamId) {
        return playerService.getPlayersByTeam(teamId);
    }

    // 특정 선수 상세 정보 조회
    @GetMapping("/players/{playerId}")
    public PlayerResponseDto.Details getPlayerDetails(@PathVariable String playerId) {
        return playerService.getPlayerDetails(playerId);
    }
}
