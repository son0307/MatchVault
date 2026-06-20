package com.son.soccerStreaming.league.controller;

import com.son.soccerStreaming.league.dto.LeagueSeasonCoverageResponseDto;
import com.son.soccerStreaming.league.dto.LeaguePlayerRankingResponseDto;
import com.son.soccerStreaming.league.dto.LeagueTeamRankingResponseDto;
import com.son.soccerStreaming.league.service.LeaguePlayerRankingService;
import com.son.soccerStreaming.league.service.LeagueSeasonCoverageService;
import com.son.soccerStreaming.league.service.LeagueTeamRankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/leagues")
@RequiredArgsConstructor
public class LeagueController {

    private static final int PREMIER_LEAGUE_ID = 39;

    private final LeagueSeasonCoverageService leagueSeasonCoverageService;
    private final LeaguePlayerRankingService leaguePlayerRankingService;
    private final LeagueTeamRankingService leagueTeamRankingService;

    @GetMapping("/{leagueId}/seasons")
    public ResponseEntity<LeagueSeasonCoverageResponseDto> getLeagueSeasons(@PathVariable Integer leagueId) {
        validateLeague(leagueId);
        return ResponseEntity.ok(leagueSeasonCoverageService.getSeasons(leagueId));
    }

    @GetMapping("/{leagueId}/player-rankings")
    public ResponseEntity<LeaguePlayerRankingResponseDto> getPlayerRankings(
            @PathVariable Integer leagueId,
            @RequestParam(defaultValue = "2025") Integer season
    ) {
        validateLeague(leagueId);
        return ResponseEntity.ok(leaguePlayerRankingService.getRankings(leagueId, season));
    }

    @GetMapping("/{leagueId}/team-rankings")
    public ResponseEntity<LeagueTeamRankingResponseDto> getTeamRankings(
            @PathVariable Integer leagueId,
            @RequestParam(defaultValue = "2025") Integer season
    ) {
        validateLeague(leagueId);
        return ResponseEntity.ok(leagueTeamRankingService.getRankings(leagueId, season));
    }

    private void validateLeague(Integer leagueId) {
        if (!Integer.valueOf(PREMIER_LEAGUE_ID).equals(leagueId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported league.");
        }
    }
}
