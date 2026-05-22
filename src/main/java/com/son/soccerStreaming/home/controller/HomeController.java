package com.son.soccerStreaming.home.controller;

import com.son.soccerStreaming.auth.security.AuthUserDetails;
import com.son.soccerStreaming.home.dto.HomeSummaryResponseDto;
import com.son.soccerStreaming.home.service.HomeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Home", description = "Home summary API")
@RestController
@RequestMapping("/api/v1/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    @Operation(summary = "Home summary", description = "Returns today's fixtures, standings, and favorite dashboard data.")
    @GetMapping("/summary")
    public ResponseEntity<HomeSummaryResponseDto> getSummary(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @Parameter(description = "Season to query", example = "2025")
            @RequestParam(defaultValue = "2025") Integer season
    ) {
        Long userId = userDetails != null ? userDetails.getId() : null;
        return ResponseEntity.ok(homeService.getSummary(season, userId));
    }
}
