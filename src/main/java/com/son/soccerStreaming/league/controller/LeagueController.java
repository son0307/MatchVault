package com.son.soccerStreaming.league.controller;

import com.son.soccerStreaming.league.dto.LeagueSeasonCoverageResponseDto;
import com.son.soccerStreaming.league.service.LeagueSeasonCoverageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/leagues")
@RequiredArgsConstructor
public class LeagueController {

    private static final int PREMIER_LEAGUE_ID = 39;

    private final LeagueSeasonCoverageService leagueSeasonCoverageService;

    @GetMapping("/{leagueId}/seasons")
    public ResponseEntity<LeagueSeasonCoverageResponseDto> getLeagueSeasons(@PathVariable Integer leagueId) {
        if (!Integer.valueOf(PREMIER_LEAGUE_ID).equals(leagueId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported league.");
        }
        return ResponseEntity.ok(leagueSeasonCoverageService.getSeasons(leagueId));
    }
}
