package com.son.soccerStreaming.controller;

import com.son.soccerStreaming.dto.PlayerResponseDto;
import com.son.soccerStreaming.dto.TeamResponseDto;
import com.son.soccerStreaming.dto.TeamStandingResponseDto;
import com.son.soccerStreaming.service.TeamService;
import com.son.soccerStreaming.service.TeamStandingService;
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

@Tag(name = "Team Information", description = "Team information API")
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
            @Parameter(description = "조회할 시즌", example = "2019")
            @RequestParam(defaultValue = "2019") Integer season
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
            @PathVariable Long teamId
    ) {
        return teamService.getPlayersByTeam(teamId);
    }
}
