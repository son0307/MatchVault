package com.son.soccerStreaming.controller;

import com.son.soccerStreaming.dto.PlayerResponseDto;
import com.son.soccerStreaming.service.TeamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Team Information", description = "팀 정보 조회 API")
@RestController
@RequestMapping("/api/v1/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    // 특정 팀 소속 선수 조회
    @Operation(summary = "팀 소속 선수 목록 조회", description = "팀에 소속되어 있는 선수들의 목록을 조회합니다")
    @GetMapping("/{teamId}/players")
    public List<PlayerResponseDto.Summary> getTeamPlayers(
            @Parameter(description = "조회할 팀의 고유 ID (예: team_tottenham)")
            @PathVariable Long teamId) {
        return teamService.getPlayersByTeam(teamId);
    }
}
