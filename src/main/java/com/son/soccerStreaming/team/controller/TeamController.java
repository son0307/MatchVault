package com.son.soccerStreaming.team.controller;

import com.son.soccerStreaming.player.dto.PlayerResponseDto;
import com.son.soccerStreaming.team.dto.TeamResponseDto;
import com.son.soccerStreaming.team.dto.TeamStandingResponseDto;
import com.son.soccerStreaming.team.service.TeamService;
import com.son.soccerStreaming.team.service.TeamStandingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "팀 정보", description = "팀 정보 API")
@RestController
@RequestMapping("/api/v1/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;
    private final TeamStandingService teamStandingService;

    @Operation(summary = "팀 목록 조회", description = "저장된 팀의 기본 정보를 조회합니다.")
    @GetMapping
    public List<TeamResponseDto.Summary> getTeams() {
        return teamService.getTeams();
    }

    @Operation(summary = "팀 순위 조회", description = "EPL 시즌 팀 순위를 조회합니다.")
    @GetMapping("/standings")
    public List<TeamStandingResponseDto> getStandings(
            @Parameter(description = "조회할 시즌", example = "2025")
            @RequestParam(defaultValue = "2025") Integer season
    ) {
        return teamStandingService.getStandings(season);
    }

    @Operation(summary = "팀 상세 조회", description = "특정 팀의 상세 정보와 홈 경기장 정보를 조회합니다.")
    @GetMapping("/{teamId}")
    public TeamResponseDto.Details getTeamDetails(
            @Parameter(description = "조회할 팀의 API ID", example = "47")
            @PathVariable Long teamId
    ) {
        return teamService.getTeamDetails(teamId);
    }

    @Operation(summary = "팀 소속 선수 목록 조회", description = "특정 팀에 소속된 선수 목록을 조회합니다.")
    @GetMapping("/{teamId}/players")
    public List<PlayerResponseDto.Summary> getTeamPlayers(
            @Parameter(description = "조회할 팀의 API ID", example = "47")
            @PathVariable Long teamId,
            @Parameter(description = "조회할 시즌", example = "2025")
            @RequestParam(defaultValue = "2025") Integer season
    ) {
        return teamService.getPlayersByTeam(teamId, season);
    }
}
