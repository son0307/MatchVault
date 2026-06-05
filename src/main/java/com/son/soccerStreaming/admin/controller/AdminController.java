package com.son.soccerStreaming.admin.controller;

import com.son.soccerStreaming.admin.dto.AdminDto;
import com.son.soccerStreaming.auth.security.AuthUserDetails;
import com.son.soccerStreaming.admin.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/teams")
    public List<AdminDto.TeamAdminResponse> searchTeams(@RequestParam(defaultValue = "") String keyword) {
        return adminService.searchTeams(keyword);
    }

    @GetMapping("/teams/{teamId}")
    public ResponseEntity<AdminDto.TeamAdminResponse> getTeam(@PathVariable Long teamId) {
        return ResponseEntity.ok(adminService.getTeamAdminDetail(teamId));
    }

    @PutMapping("/teams/{teamId}")
    public ResponseEntity<AdminDto.TeamAdminResponse> updateTeam(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long teamId,
            @RequestBody AdminDto.TeamUpdateRequest request
    ) {
        return ResponseEntity.ok(adminService.updateTeam(userDetails.getId(), teamId, request));
    }

    @DeleteMapping("/teams/{teamId}/overrides")
    public ResponseEntity<AdminDto.TeamAdminResponse> clearTeamOverrides(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long teamId
    ) {
        return ResponseEntity.ok(adminService.clearTeamOverrides(userDetails.getId(), teamId));
    }

    @DeleteMapping("/teams/{teamId}/overrides/{fieldName}")
    public ResponseEntity<AdminDto.TeamAdminResponse> clearTeamOverride(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long teamId,
            @PathVariable String fieldName
    ) {
        return ResponseEntity.ok(adminService.clearTeamOverride(userDetails.getId(), teamId, fieldName));
    }

    @GetMapping("/players")
    public List<AdminDto.PlayerAdminResponse> searchPlayers(@RequestParam(defaultValue = "") String keyword) {
        return adminService.searchPlayers(keyword);
    }

    @GetMapping("/players/{playerId}")
    public ResponseEntity<AdminDto.PlayerAdminResponse> getPlayer(@PathVariable Long playerId) {
        return ResponseEntity.ok(adminService.getPlayerAdminDetail(playerId));
    }

    @GetMapping("/fixtures")
    public List<AdminDto.FixtureAdminSummaryResponse> searchFixtures(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) Integer season
    ) {
        return adminService.searchFixtures(keyword, season);
    }

    @GetMapping("/fixtures/{fixtureId}")
    public ResponseEntity<AdminDto.FixtureAdminDetailResponse> getFixture(
            @PathVariable Long fixtureId
    ) {
        return ResponseEntity.ok(adminService.getFixtureAdminDetail(fixtureId));
    }

    @PutMapping("/fixtures/{fixtureId}")
    public ResponseEntity<AdminDto.FixtureAdminDetailResponse> updateFixture(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long fixtureId,
            @RequestBody AdminDto.FixtureUpdateRequest request
    ) {
        return ResponseEntity.ok(adminService.updateFixture(userDetails.getId(), fixtureId, request));
    }

    @PutMapping("/fixtures/{fixtureId}/events/{eventSequence}")
    public ResponseEntity<AdminDto.FixtureAdminDetailResponse> updateFixtureEvent(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long fixtureId,
            @PathVariable Integer eventSequence,
            @RequestBody AdminDto.FixtureEventUpdateRequest request
    ) {
        return ResponseEntity.ok(adminService.updateFixtureEvent(userDetails.getId(), fixtureId, eventSequence, request));
    }

    @PostMapping("/fixtures/{fixtureId}/events")
    public ResponseEntity<AdminDto.FixtureAdminDetailResponse> createFixtureEvent(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long fixtureId,
            @RequestBody AdminDto.FixtureEventUpdateRequest request
    ) {
        return ResponseEntity.ok(adminService.createFixtureEvent(userDetails.getId(), fixtureId, request));
    }

    @PutMapping("/fixtures/{fixtureId}/lineups/{teamId}/{playerId}")
    public ResponseEntity<AdminDto.FixtureAdminDetailResponse> updateFixtureLineup(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long fixtureId,
            @PathVariable Long teamId,
            @PathVariable Long playerId,
            @RequestBody AdminDto.FixtureLineupUpdateRequest request
    ) {
        return ResponseEntity.ok(adminService.updateFixtureLineup(userDetails.getId(), fixtureId, teamId, playerId, request));
    }

    @PutMapping("/fixtures/{fixtureId}/team-stats/{teamId}")
    public ResponseEntity<AdminDto.FixtureAdminDetailResponse> updateFixtureTeamStat(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long fixtureId,
            @PathVariable Long teamId,
            @RequestBody AdminDto.FixtureTeamStatUpdateRequest request
    ) {
        return ResponseEntity.ok(adminService.updateFixtureTeamStat(userDetails.getId(), fixtureId, teamId, request));
    }

    @PutMapping("/fixtures/{fixtureId}/player-stats/{playerId}")
    public ResponseEntity<AdminDto.FixtureAdminDetailResponse> updateFixturePlayerStat(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long fixtureId,
            @PathVariable Long playerId,
            @RequestBody AdminDto.FixturePlayerStatUpdateRequest request
    ) {
        return ResponseEntity.ok(adminService.updateFixturePlayerStat(userDetails.getId(), fixtureId, playerId, request));
    }

    @PutMapping("/players/{playerId}")
    public ResponseEntity<AdminDto.PlayerAdminResponse> updatePlayer(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long playerId,
            @RequestBody AdminDto.PlayerUpdateRequest request
    ) {
        return ResponseEntity.ok(adminService.updatePlayer(userDetails.getId(), playerId, request));
    }

    @DeleteMapping("/players/{playerId}/overrides")
    public ResponseEntity<AdminDto.PlayerAdminResponse> clearPlayerOverrides(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long playerId
    ) {
        return ResponseEntity.ok(adminService.clearPlayerOverrides(userDetails.getId(), playerId));
    }

    @DeleteMapping("/players/{playerId}/overrides/{fieldName}")
    public ResponseEntity<AdminDto.PlayerAdminResponse> clearPlayerOverride(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long playerId,
            @PathVariable String fieldName
    ) {
        return ResponseEntity.ok(adminService.clearPlayerOverride(userDetails.getId(), playerId, fieldName));
    }

    @PostMapping("/sync/teams")
    public ResponseEntity<AdminDto.SyncResponse> syncTeams(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @RequestParam(defaultValue = "39") Integer league,
            @RequestParam(defaultValue = "2025") Integer season
    ) {
        return ResponseEntity.ok(adminService.syncTeams(userDetails.getId(), league, season));
    }

    @PostMapping("/sync/standings")
    public ResponseEntity<AdminDto.SyncResponse> syncStandings(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @RequestParam(defaultValue = "39") Integer league,
            @RequestParam(defaultValue = "2025") Integer season
    ) {
        return ResponseEntity.ok(adminService.syncStandings(userDetails.getId(), league, season));
    }

    @PostMapping("/sync/fixtures")
    public ResponseEntity<AdminDto.SyncResponse> syncFixtures(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @RequestParam(defaultValue = "39") Integer league,
            @RequestParam(defaultValue = "2025") Integer season
    ) {
        return ResponseEntity.ok(adminService.syncFixtures(userDetails.getId(), league, season));
    }

    @PostMapping("/sync/fixture-details")
    public ResponseEntity<AdminDto.SyncResponse> syncSeasonFixtureDetails(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @RequestParam(defaultValue = "2025") Integer season
    ) {
        return ResponseEntity.ok(adminService.syncSeasonFixtureDetails(userDetails.getId(), season));
    }

    @PostMapping("/sync/fixture-details/{fixtureId}")
    public ResponseEntity<AdminDto.SyncResponse> syncFixtureDetail(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long fixtureId
    ) {
        return ResponseEntity.ok(adminService.syncFixtureDetail(userDetails.getId(), fixtureId));
    }

    @PostMapping("/sync/players")
    public ResponseEntity<AdminDto.SyncResponse> syncPlayers(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @RequestParam(defaultValue = "39") Integer league,
            @RequestParam(defaultValue = "2025") Integer season,
            @RequestParam(defaultValue = "7000") Long delayMs
    ) {
        return ResponseEntity.ok(adminService.syncPlayers(userDetails.getId(), league, season, delayMs));
    }

    @PostMapping("/sync/injuries")
    public ResponseEntity<AdminDto.SyncResponse> syncInjuries(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @RequestParam(defaultValue = "39") Integer league,
            @RequestParam(defaultValue = "2025") Integer season
    ) {
        return ResponseEntity.ok(adminService.syncInjuries(userDetails.getId(), league, season));
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<AdminDto.AuditLogListResponse> getAuditLogs(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size
    ) {
        return ResponseEntity.ok(adminService.getAuditLogs(page, size));
    }

    @GetMapping("/sync/statuses")
    public ResponseEntity<AdminDto.SyncStatusResponse> getSyncStatuses(
            @RequestParam(defaultValue = "2025") Integer season
    ) {
        return ResponseEntity.ok(adminService.getSyncStatuses(season));
    }

}
