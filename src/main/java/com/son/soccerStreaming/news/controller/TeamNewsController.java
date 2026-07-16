package com.son.soccerStreaming.news.controller;

import com.son.soccerStreaming.news.dto.TeamNewsListResponseDto;
import com.son.soccerStreaming.news.service.TeamNewsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Team News", description = "Stored team news API")
@RestController
@RequestMapping("/api/v1/teams/{teamId}/news")
@RequiredArgsConstructor
public class TeamNewsController {

    private final TeamNewsService teamNewsService;

    @Operation(summary = "Get latest team news")
    @GetMapping
    public TeamNewsListResponseDto getTeamNews(@PathVariable Long teamId) {
        return teamNewsService.getTeamNews(teamId);
    }
}
