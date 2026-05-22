package com.son.soccerStreaming.admin.service;

import com.son.soccerStreaming.apifootball.service.ApiFootballFixtureDetailSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballFixtureSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballInjurySyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballPlayerSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballStandingSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballTeamSyncService;
import com.son.soccerStreaming.admin.dto.AdminDto;
import com.son.soccerStreaming.admin.entity.AdminAuditLog;
import com.son.soccerStreaming.admin.entity.AdminAuditType;
import com.son.soccerStreaming.admin.entity.AdminOverrideTargetType;
import com.son.soccerStreaming.auth.entity.AppUser;
import com.son.soccerStreaming.player.entity.Player;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.team.entity.Venue;
import com.son.soccerStreaming.global.exception.CustomException;
import com.son.soccerStreaming.global.exception.ErrorCode;
import com.son.soccerStreaming.admin.repository.AdminAuditLogRepository;
import com.son.soccerStreaming.apifootball.repository.ApiFootballSyncStatusRepository;
import com.son.soccerStreaming.auth.repository.AppUserRepository;
import com.son.soccerStreaming.player.repository.PlayerRepository;
import com.son.soccerStreaming.team.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final Set<String> TEAM_OVERRIDE_FIELDS = Set.of(
            "name",
            "code",
            "country",
            "founded",
            "logoUrl",
            "venueId",
            "venueName",
            "venueAddress",
            "venueCity",
            "capacity",
            "surface",
            "venueImageUrl"
    );
    private static final Set<String> PLAYER_OVERRIDE_FIELDS = Set.of(
            "name",
            "firstname",
            "lastname",
            "age",
            "birthDate",
            "birthPlace",
            "birthCountry",
            "nationality",
            "height",
            "weight",
            "position",
            "number",
            "photoUrl"
    );

    private static final List<SyncStatusDefinition> SYNC_STATUS_DEFINITIONS = List.of(
            new SyncStatusDefinition("teams", "Teams"),
            new SyncStatusDefinition("standings", "Standings"),
            new SyncStatusDefinition("fixtures", "Fixtures"),
            new SyncStatusDefinition("fixture-details", "Season Details"),
            new SyncStatusDefinition("fixture-detail", "Fixture Detail"),
            new SyncStatusDefinition("players", "Players"),
            new SyncStatusDefinition("injuries", "Injuries")
    );

    private final AppUserRepository appUserRepository;
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final AdminOverrideService adminOverrideService;
    private final AdminAuditLogRepository adminAuditLogRepository;
    private final ApiFootballSyncStatusRepository apiFootballSyncStatusRepository;
    private final ApiFootballTeamSyncService apiFootballTeamSyncService;
    private final ApiFootballStandingSyncService apiFootballStandingSyncService;
    private final ApiFootballFixtureSyncService apiFootballFixtureSyncService;
    private final ApiFootballFixtureDetailSyncService apiFootballFixtureDetailSyncService;
    private final ApiFootballPlayerSyncService apiFootballPlayerSyncService;
    private final ApiFootballInjurySyncService apiFootballInjurySyncService;
    private final AdminSyncTaskRunner adminSyncTaskRunner;

    @Transactional(readOnly = true)
    public List<AdminDto.TeamAdminResponse> searchTeams(String keyword) {
        String search = keyword == null ? "" : keyword.trim();
        return teamRepository.findTop20ByNameContainingIgnoreCaseOrderByNameAsc(search)
                .stream()
                .map(this::toTeamResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminDto.PlayerAdminResponse> searchPlayers(String keyword) {
        String search = keyword == null ? "" : keyword.trim();
        return playerRepository.findTop20ByNameContainingIgnoreCaseOrderByNameAsc(search)
                .stream()
                .map(this::toPlayerResponse)
                .toList();
    }

    @Transactional
    public AdminDto.TeamAdminResponse updateTeam(Long adminUserId, Long teamId, AdminDto.TeamUpdateRequest request) {
        Team team = teamRepository.findByTeamId(teamId)
                .orElseThrow(() -> new CustomException(ErrorCode.TEAM_NOT_FOUND));
        List<FieldChange> changes = changedTeamFields(team, request);
        List<String> changedFields = fieldNames(changes);

        team.updateTeam(
                request.getName(),
                request.getCode(),
                request.getCountry(),
                request.getFounded(),
                request.getLogoUrl()
        );
        upsertVenue(team, request);
        if (!changedFields.isEmpty()) {
            adminOverrideService.markOverrides(AdminOverrideTargetType.TEAM, team.getTeamId(), changedFields);
        }
        adminAuditLogRepository.save(AdminAuditLog.of(
                findUser(adminUserId),
                AdminAuditType.TEAM_UPDATE,
                "TEAM",
                team.getTeamId(),
                "Team profile updated: " + team.getName(),
                detailsOf(changes),
                true
        ));
        return toTeamResponse(team);
    }

    @Transactional
    public AdminDto.PlayerAdminResponse updatePlayer(Long adminUserId, Long playerId, AdminDto.PlayerUpdateRequest request) {
        Player player = playerRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new CustomException(ErrorCode.PLAYER_NOT_FOUND));
        List<FieldChange> changes = changedPlayerFields(player, request);
        List<String> changedFields = fieldNames(changes);

        player.updateProfile(
                request.getName(),
                request.getFirstname(),
                request.getLastname(),
                request.getAge(),
                request.getBirthDate(),
                request.getBirthPlace(),
                request.getBirthCountry(),
                request.getNationality(),
                request.getHeight(),
                request.getWeight(),
                request.getPosition(),
                request.getNumber(),
                request.getPhotoUrl()
        );
        if (!changedFields.isEmpty()) {
            adminOverrideService.markOverrides(AdminOverrideTargetType.PLAYER, player.getPlayerId(), changedFields);
        }
        adminAuditLogRepository.save(AdminAuditLog.of(
                findUser(adminUserId),
                AdminAuditType.PLAYER_UPDATE,
                "PLAYER",
                player.getPlayerId(),
                "Player profile updated: " + player.getName(),
                detailsOf(changes),
                true
        ));
        return toPlayerResponse(player);
    }

    @Transactional
    public AdminDto.TeamAdminResponse clearTeamOverride(Long adminUserId, Long teamId, String fieldName) {
        validateOverrideField(TEAM_OVERRIDE_FIELDS, fieldName);
        Team team = teamRepository.findByTeamId(teamId)
                .orElseThrow(() -> new CustomException(ErrorCode.TEAM_NOT_FOUND));
        long deletedCount = adminOverrideService.clearOverride(AdminOverrideTargetType.TEAM, team.getTeamId(), fieldName);
        saveOverrideClearLog(adminUserId, "TEAM", team.getTeamId(), fieldName, deletedCount);
        return toTeamResponse(team);
    }

    @Transactional
    public AdminDto.TeamAdminResponse clearTeamOverrides(Long adminUserId, Long teamId) {
        Team team = teamRepository.findByTeamId(teamId)
                .orElseThrow(() -> new CustomException(ErrorCode.TEAM_NOT_FOUND));
        long deletedCount = adminOverrideService.clearOverrides(AdminOverrideTargetType.TEAM, team.getTeamId());
        saveOverrideClearLog(adminUserId, "TEAM", team.getTeamId(), "ALL", deletedCount);
        return toTeamResponse(team);
    }

    @Transactional
    public AdminDto.PlayerAdminResponse clearPlayerOverride(Long adminUserId, Long playerId, String fieldName) {
        validateOverrideField(PLAYER_OVERRIDE_FIELDS, fieldName);
        Player player = playerRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new CustomException(ErrorCode.PLAYER_NOT_FOUND));
        long deletedCount = adminOverrideService.clearOverride(AdminOverrideTargetType.PLAYER, player.getPlayerId(), fieldName);
        saveOverrideClearLog(adminUserId, "PLAYER", player.getPlayerId(), fieldName, deletedCount);
        return toPlayerResponse(player);
    }

    @Transactional
    public AdminDto.PlayerAdminResponse clearPlayerOverrides(Long adminUserId, Long playerId) {
        Player player = playerRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new CustomException(ErrorCode.PLAYER_NOT_FOUND));
        long deletedCount = adminOverrideService.clearOverrides(AdminOverrideTargetType.PLAYER, player.getPlayerId());
        saveOverrideClearLog(adminUserId, "PLAYER", player.getPlayerId(), "ALL", deletedCount);
        return toPlayerResponse(player);
    }

    public AdminDto.SyncResponse syncTeams(Long adminUserId, Integer league, Integer season) {
        return runSync(adminUserId, "teams", "TEAM", null, () -> apiFootballTeamSyncService.syncTeams(league, season));
    }

    public AdminDto.SyncResponse syncStandings(Long adminUserId, Integer league, Integer season) {
        return runSync(adminUserId, "standings", "STANDING", null, () -> apiFootballStandingSyncService.syncStandings(league, season));
    }

    public AdminDto.SyncResponse syncFixtures(Long adminUserId, Integer league, Integer season) {
        return runSync(adminUserId, "fixtures", "FIXTURE", null, () -> apiFootballFixtureSyncService.syncSeasonFixtures(league, season));
    }

    public AdminDto.SyncResponse syncSeasonFixtureDetails(Long adminUserId, Integer season) {
        return queueSync(adminUserId, "fixture-details", "FIXTURE", null,
                () -> apiFootballFixtureDetailSyncService.syncSeasonFixtureDetails(season, false));
    }

    public AdminDto.SyncResponse syncFixtureDetail(Long adminUserId, Long fixtureId) {
        return runSync(adminUserId, "fixture-detail", "FIXTURE", fixtureId,
                () -> apiFootballFixtureDetailSyncService.syncFixtureDetail(fixtureId, false).fixtureId() != null ? 1 : 0);
    }

    public AdminDto.SyncResponse syncPlayers(Long adminUserId, Integer league, Integer season, Long delayMs) {
        return queueSync(adminUserId, "players", "PLAYER", null,
                () -> apiFootballPlayerSyncService.syncRegisteredPlayers(league, season, delayMs));
    }

    public AdminDto.SyncResponse syncInjuries(Long adminUserId, Integer league, Integer season) {
        return queueSync(adminUserId, "injuries", "INJURY", null, () -> apiFootballInjurySyncService.syncInjuries(league, season));
    }

    @Transactional(readOnly = true)
    public AdminDto.AuditLogListResponse getAuditLogs() {
        return AdminDto.AuditLogListResponse.builder()
                .logs(adminAuditLogRepository.findTop50ByOrderByCreatedAtDesc()
                        .stream()
                        .map(this::toAuditLogResponse)
                        .toList())
                .build();
    }

    @Transactional(readOnly = true)
    public AdminDto.SyncStatusResponse getSyncStatuses() {
        return AdminDto.SyncStatusResponse.builder()
                .statuses(SYNC_STATUS_DEFINITIONS.stream()
                        .map(this::toSyncStatusItem)
                        .toList())
                .build();
    }

    private AdminDto.SyncResponse runSync(Long adminUserId, String task, String targetType, Long targetId, SyncTask syncTask) {
        AppUser admin = findUser(adminUserId);
        try {
            int count = syncTask.run();
            String message = task + " sync completed. count=" + count;
            adminAuditLogRepository.save(AdminAuditLog.of(admin, AdminAuditType.SYNC, targetType, targetId, message, true));
            return AdminDto.SyncResponse.builder()
                    .task(task)
                    .success(true)
                    .count(count)
                    .message(message)
                    .build();
        } catch (Exception exception) {
            String message = task + " sync failed: " + exception.getMessage();
            adminAuditLogRepository.save(AdminAuditLog.of(admin, AdminAuditType.SYNC, targetType, targetId, message, false));
            return AdminDto.SyncResponse.builder()
                    .task(task)
                    .success(false)
                    .count(0)
                    .message(message)
                    .build();
        }
    }

    private AdminDto.SyncResponse queueSync(Long adminUserId, String task, String targetType, Long targetId,
                                            AdminSyncTaskRunner.SyncTask syncTask) {
        // Keep the admin request short; the runner writes start/completion audit logs from a worker thread.
        findUser(adminUserId);
        adminSyncTaskRunner.run(adminUserId, task, targetType, targetId, syncTask);
        return AdminDto.SyncResponse.builder()
                .task(task)
                .success(true)
                .queued(true)
                .count(0)
                .message(task + " sync has started in the background. Check audit logs for completion.")
                .build();
    }

    private void upsertVenue(Team team, AdminDto.TeamUpdateRequest request) {
        Venue venue = team.getVenue();
        if (venue == null && request.getVenueId() == null) {
            return;
        }
        if (venue == null) {
            venue = Venue.builder()
                    .venueId(request.getVenueId())
                    .build();
        }
        venue.updateVenue(
                request.getVenueName(),
                request.getVenueAddress(),
                request.getVenueCity(),
                request.getCapacity(),
                request.getSurface(),
                request.getVenueImageUrl()
        );
        team.updateVenue(venue);
    }

    private List<FieldChange> changedTeamFields(Team team, AdminDto.TeamUpdateRequest request) {
        List<FieldChange> changes = new ArrayList<>();
        addIfChanged(changes, "name", team.getName(), request.getName());
        addIfChanged(changes, "code", team.getCode(), request.getCode());
        addIfChanged(changes, "country", team.getCountry(), request.getCountry());
        addIfChanged(changes, "founded", team.getFounded(), request.getFounded());
        addIfChanged(changes, "logoUrl", team.getLogoUrl(), request.getLogoUrl());

        Venue venue = team.getVenue();
        if (venue != null || request.getVenueId() != null) {
            addIfChanged(changes, "venueId", venue != null ? venue.getVenueId() : null, request.getVenueId());
            addIfChanged(changes, "venueName", venue != null ? venue.getVenueName() : null, request.getVenueName());
            addIfChanged(changes, "venueAddress", venue != null ? venue.getVenueAddress() : null, request.getVenueAddress());
            addIfChanged(changes, "venueCity", venue != null ? venue.getVenueCity() : null, request.getVenueCity());
            addIfChanged(changes, "capacity", venue != null ? venue.getCapacity() : null, request.getCapacity());
            addIfChanged(changes, "surface", venue != null ? venue.getSurface() : null, request.getSurface());
            addIfChanged(changes, "venueImageUrl", venue != null ? venue.getVenueImageUrl() : null, request.getVenueImageUrl());
        }

        return changes;
    }

    private List<FieldChange> changedPlayerFields(Player player, AdminDto.PlayerUpdateRequest request) {
        List<FieldChange> changes = new ArrayList<>();
        addIfChanged(changes, "name", player.getName(), request.getName());
        addIfChanged(changes, "firstname", player.getFirstname(), request.getFirstname());
        addIfChanged(changes, "lastname", player.getLastname(), request.getLastname());
        addIfChanged(changes, "age", player.getAge(), request.getAge());
        addIfChanged(changes, "birthDate", player.getBirthDate(), request.getBirthDate());
        addIfChanged(changes, "birthPlace", player.getBirthPlace(), request.getBirthPlace());
        addIfChanged(changes, "birthCountry", player.getBirthCountry(), request.getBirthCountry());
        addIfChanged(changes, "nationality", player.getNationality(), request.getNationality());
        addIfChanged(changes, "height", player.getHeight(), request.getHeight());
        addIfChanged(changes, "weight", player.getWeight(), request.getWeight());
        addIfChanged(changes, "position", player.getPosition(), request.getPosition());
        addIfChanged(changes, "number", player.getNumber(), request.getNumber());
        addIfChanged(changes, "photoUrl", player.getPhotoUrl(), request.getPhotoUrl());
        return changes;
    }

    private void addIfChanged(List<FieldChange> changes, String fieldName, Object currentValue, Object requestValue) {
        if (!Objects.equals(currentValue, requestValue)) {
            changes.add(new FieldChange(fieldName, currentValue, requestValue));
        }
    }

    private List<String> fieldNames(List<FieldChange> changes) {
        return changes.stream()
                .map(FieldChange::fieldName)
                .toList();
    }

    private void validateOverrideField(Set<String> allowedFields, String fieldName) {
        if (!allowedFields.contains(fieldName)) {
            throw new CustomException(ErrorCode.INVALID_ADMIN_OVERRIDE_FIELD);
        }
    }

    private void saveOverrideClearLog(Long adminUserId, String targetType, Long targetId, String fieldName, long deletedCount) {
        String target = "ALL".equals(fieldName) ? "all manual overrides" : "manual override field=" + fieldName;
        adminAuditLogRepository.save(AdminAuditLog.of(
                findUser(adminUserId),
                AdminAuditType.OVERRIDE_CLEAR,
                targetType,
                targetId,
                "Cleared " + target + " for " + targetType + " #" + targetId,
                "field=" + fieldName + "; deletedCount=" + deletedCount,
                true
        ));
    }

    private String detailsOf(List<FieldChange> changes) {
        if (changes.isEmpty()) {
            return "changedFields=[]";
        }
        return changes.stream()
                .map(change -> change.fieldName() + ": " + valueText(change.previousValue()) + " -> " + valueText(change.newValue()))
                .collect(Collectors.joining("; "));
    }

    private String valueText(Object value) {
        return value == null ? "null" : String.valueOf(value);
    }

    private record FieldChange(String fieldName, Object previousValue, Object newValue) {
    }

    private AppUser findUser(Long userId) {
        return appUserRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private AdminDto.TeamAdminResponse toTeamResponse(Team team) {
        Venue venue = team.getVenue();
        return AdminDto.TeamAdminResponse.builder()
                .teamId(team.getTeamId())
                .name(team.getName())
                .code(team.getCode())
                .country(team.getCountry())
                .founded(team.getFounded())
                .logoUrl(team.getLogoUrl())
                .venueId(venue != null ? venue.getVenueId() : null)
                .venueName(venue != null ? venue.getVenueName() : null)
                .venueAddress(venue != null ? venue.getVenueAddress() : null)
                .venueCity(venue != null ? venue.getVenueCity() : null)
                .capacity(venue != null ? venue.getCapacity() : null)
                .surface(venue != null ? venue.getSurface() : null)
                .venueImageUrl(venue != null ? venue.getVenueImageUrl() : null)
                .manualOverrides(toOverrideResponses(AdminOverrideTargetType.TEAM, team.getTeamId()))
                .build();
    }

    private AdminDto.PlayerAdminResponse toPlayerResponse(Player player) {
        return AdminDto.PlayerAdminResponse.builder()
                .playerId(player.getPlayerId())
                .name(player.getName())
                .firstname(player.getFirstname())
                .lastname(player.getLastname())
                .age(player.getAge())
                .birthDate(player.getBirthDate())
                .birthPlace(player.getBirthPlace())
                .birthCountry(player.getBirthCountry())
                .nationality(player.getNationality())
                .height(player.getHeight())
                .weight(player.getWeight())
                .position(player.getPosition())
                .number(player.getNumber())
                .photoUrl(player.getPhotoUrl())
                .manualOverrides(toOverrideResponses(AdminOverrideTargetType.PLAYER, player.getPlayerId()))
                .build();
    }

    private List<AdminDto.ManualOverrideResponse> toOverrideResponses(AdminOverrideTargetType targetType, Long targetId) {
        List<AdminOverrideService.OverrideInfo> overrides = adminOverrideService.overrideInfos(targetType, targetId);
        if (overrides == null) {
            return List.of();
        }
        return overrides.stream()
                .map(override -> AdminDto.ManualOverrideResponse.builder()
                        .fieldName(override.fieldName())
                        .updatedAt(override.updatedAt())
                        .build())
                .toList();
    }

    private AdminDto.AuditLogResponse toAuditLogResponse(AdminAuditLog log) {
        return AdminDto.AuditLogResponse.builder()
                .id(log.getId())
                .adminEmail(log.getAdminUser() != null ? log.getAdminUser().getEmail() : null)
                .type(log.getType().name())
                .targetType(log.getTargetType())
                .targetId(log.getTargetId())
                .message(log.getMessage())
                .details(log.getDetails())
                .success(log.isSuccess())
                .createdAt(log.getCreatedAt())
                .build();
    }

    private AdminDto.SyncStatusItem toSyncStatusItem(SyncStatusDefinition definition) {
        return AdminDto.SyncStatusItem.builder()
                .task(definition.task())
                .label(definition.label())
                .lastSyncedAt(latestCompletedSyncTime(definition.task()))
                .build();
    }

    private OffsetDateTime latestCompletedSyncTime(String task) {
        return apiFootballSyncStatusRepository.findById(task)
                .map(status -> status.getLastSyncedAt())
                .map(this::toKoreaOffsetDateTime)
                .orElse(null);
    }

    private OffsetDateTime toKoreaOffsetDateTime(LocalDateTime dateTime) {
        return dateTime.atZone(KOREA_ZONE).toOffsetDateTime();
    }

    private record SyncStatusDefinition(String task, String label) {
    }

    @FunctionalInterface
    private interface SyncTask {
        int run();
    }
}
