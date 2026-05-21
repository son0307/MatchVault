package com.son.soccerStreaming.favorite.controller;

import com.son.soccerStreaming.favorite.dto.FavoriteDashboardResponseDto;
import com.son.soccerStreaming.auth.security.AuthUserDetails;
import com.son.soccerStreaming.favorite.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Favorites", description = "즐겨찾기와 개인 대시보드 API")
@RestController
@RequestMapping("/api/v1/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @Operation(summary = "즐겨찾기 대시보드", description = "즐겨찾기한 팀과 선수의 요약 정보를 조회합니다.")
    @GetMapping("/dashboard")
    public ResponseEntity<FavoriteDashboardResponseDto> getDashboard(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @RequestParam(defaultValue = "2025") Integer season
    ) {
        return ResponseEntity.ok(favoriteService.getDashboard(userDetails.getId(), season));
    }

    @Operation(summary = "팀 즐겨찾기 등록")
    @PostMapping("/teams/{teamId}")
    public ResponseEntity<FavoriteDashboardResponseDto> addTeam(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long teamId,
            @RequestParam(defaultValue = "2025") Integer season
    ) {
        return ResponseEntity.ok(favoriteService.addTeam(userDetails.getId(), teamId, season));
    }

    @Operation(summary = "팀 즐겨찾기 해제")
    @DeleteMapping("/teams/{teamId}")
    public ResponseEntity<FavoriteDashboardResponseDto> removeTeam(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long teamId,
            @RequestParam(defaultValue = "2025") Integer season
    ) {
        return ResponseEntity.ok(favoriteService.removeTeam(userDetails.getId(), teamId, season));
    }

    @Operation(summary = "선수 즐겨찾기 등록")
    @PostMapping("/players/{playerId}")
    public ResponseEntity<FavoriteDashboardResponseDto> addPlayer(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long playerId,
            @RequestParam(defaultValue = "2025") Integer season
    ) {
        return ResponseEntity.ok(favoriteService.addPlayer(userDetails.getId(), playerId, season));
    }

    @Operation(summary = "선수 즐겨찾기 해제")
    @DeleteMapping("/players/{playerId}")
    public ResponseEntity<FavoriteDashboardResponseDto> removePlayer(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long playerId,
            @RequestParam(defaultValue = "2025") Integer season
    ) {
        return ResponseEntity.ok(favoriteService.removePlayer(userDetails.getId(), playerId, season));
    }
}
