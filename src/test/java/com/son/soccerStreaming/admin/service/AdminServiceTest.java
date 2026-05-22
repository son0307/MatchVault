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
import com.son.soccerStreaming.apifootball.repository.ApiFootballSyncStatusRepository;
import com.son.soccerStreaming.auth.entity.AppUser;
import com.son.soccerStreaming.global.exception.CustomException;
import com.son.soccerStreaming.player.entity.Player;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.admin.repository.AdminAuditLogRepository;
import com.son.soccerStreaming.auth.repository.AppUserRepository;
import com.son.soccerStreaming.player.repository.PlayerRepository;
import com.son.soccerStreaming.team.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private AdminOverrideService adminOverrideService;
    @Mock
    private AdminAuditLogRepository adminAuditLogRepository;
    @Mock
    private ApiFootballSyncStatusRepository apiFootballSyncStatusRepository;
    @Mock
    private ApiFootballTeamSyncService apiFootballTeamSyncService;
    @Mock
    private ApiFootballStandingSyncService apiFootballStandingSyncService;
    @Mock
    private ApiFootballFixtureSyncService apiFootballFixtureSyncService;
    @Mock
    private ApiFootballFixtureDetailSyncService apiFootballFixtureDetailSyncService;
    @Mock
    private ApiFootballPlayerSyncService apiFootballPlayerSyncService;
    @Mock
    private ApiFootballInjurySyncService apiFootballInjurySyncService;
    @Mock
    private AdminSyncTaskRunner adminSyncTaskRunner;

    @InjectMocks
    private AdminService adminService;

    @Test
    @SuppressWarnings("unchecked")
    void updateTeamMarksOnlyChangedFieldsAsManualOverrides() {
        AppUser admin = AppUser.builder()
                .email("admin@example.com")
                .password("password")
                .nickname("Admin")
                .build();
        Team team = Team.builder()
                .teamId(47L)
                .name("Old")
                .build();
        AdminDto.TeamUpdateRequest request = new AdminDto.TeamUpdateRequest(
                "New Team",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        when(teamRepository.findByTeamId(47L)).thenReturn(Optional.of(team));
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(admin));

        AdminDto.TeamAdminResponse response = adminService.updateTeam(1L, 47L, request);

        assertThat(response.getName()).isEqualTo("New Team");
        ArgumentCaptor<List<String>> fieldsCaptor = ArgumentCaptor.forClass(List.class);
        verify(adminOverrideService).markOverrides(eq(AdminOverrideTargetType.TEAM), eq(47L), fieldsCaptor.capture());
        assertThat(fieldsCaptor.getValue()).containsExactly("name");
        ArgumentCaptor<AdminAuditLog> logCaptor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(adminAuditLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getDetails()).isEqualTo("name: Old -> New Team");
    }

    @Test
    void syncPlayersQueuesBackgroundTask() {
        AppUser admin = AppUser.builder()
                .email("admin@example.com")
                .password("password")
                .nickname("Admin")
                .build();

        when(appUserRepository.findById(1L)).thenReturn(Optional.of(admin));

        AdminDto.SyncResponse response = adminService.syncPlayers(1L, 39, 2025, 7000L);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isQueued()).isTrue();
        verify(adminSyncTaskRunner).run(
                eq(1L),
                eq("players"),
                eq("PLAYER"),
                eq(null),
                any(AdminSyncTaskRunner.SyncTask.class)
        );
    }

    @Test
    void clearTeamOverrideRemovesOneManualOverrideAndWritesAuditLog() {
        AppUser admin = adminUser();
        Team team = Team.builder()
                .teamId(47L)
                .name("Manual Team")
                .build();

        when(teamRepository.findByTeamId(47L)).thenReturn(Optional.of(team));
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(adminOverrideService.clearOverride(AdminOverrideTargetType.TEAM, 47L, "name")).thenReturn(1L);
        when(adminOverrideService.overrideInfos(AdminOverrideTargetType.TEAM, 47L)).thenReturn(List.of());

        AdminDto.TeamAdminResponse response = adminService.clearTeamOverride(1L, 47L, "name");

        assertThat(response.getManualOverrides()).isEmpty();
        verify(adminOverrideService).clearOverride(AdminOverrideTargetType.TEAM, 47L, "name");
        ArgumentCaptor<AdminAuditLog> logCaptor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(adminAuditLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getType()).isEqualTo(AdminAuditType.OVERRIDE_CLEAR);
        assertThat(logCaptor.getValue().getDetails()).isEqualTo("field=name; deletedCount=1");
    }

    @Test
    void clearTeamOverridesRemovesAllManualOverrides() {
        AppUser admin = adminUser();
        Team team = Team.builder()
                .teamId(47L)
                .name("Manual Team")
                .build();

        when(teamRepository.findByTeamId(47L)).thenReturn(Optional.of(team));
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(adminOverrideService.clearOverrides(AdminOverrideTargetType.TEAM, 47L)).thenReturn(3L);
        when(adminOverrideService.overrideInfos(AdminOverrideTargetType.TEAM, 47L)).thenReturn(List.of());

        AdminDto.TeamAdminResponse response = adminService.clearTeamOverrides(1L, 47L);

        assertThat(response.getManualOverrides()).isEmpty();
        verify(adminOverrideService).clearOverrides(AdminOverrideTargetType.TEAM, 47L);
        ArgumentCaptor<AdminAuditLog> logCaptor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(adminAuditLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getDetails()).isEqualTo("field=ALL; deletedCount=3");
    }

    @Test
    void clearPlayerOverrideRemovesOneManualOverrideAndWritesAuditLog() {
        AppUser admin = adminUser();
        Player player = Player.builder()
                .playerId(10L)
                .name("Manual Player")
                .build();

        when(playerRepository.findByPlayerId(10L)).thenReturn(Optional.of(player));
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(adminOverrideService.clearOverride(AdminOverrideTargetType.PLAYER, 10L, "photoUrl")).thenReturn(1L);
        when(adminOverrideService.overrideInfos(AdminOverrideTargetType.PLAYER, 10L)).thenReturn(List.of());

        AdminDto.PlayerAdminResponse response = adminService.clearPlayerOverride(1L, 10L, "photoUrl");

        assertThat(response.getManualOverrides()).isEmpty();
        verify(adminOverrideService).clearOverride(AdminOverrideTargetType.PLAYER, 10L, "photoUrl");
        ArgumentCaptor<AdminAuditLog> logCaptor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(adminAuditLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getType()).isEqualTo(AdminAuditType.OVERRIDE_CLEAR);
        assertThat(logCaptor.getValue().getDetails()).isEqualTo("field=photoUrl; deletedCount=1");
    }

    @Test
    void clearPlayerOverridesRemovesAllManualOverrides() {
        AppUser admin = adminUser();
        Player player = Player.builder()
                .playerId(10L)
                .name("Manual Player")
                .build();

        when(playerRepository.findByPlayerId(10L)).thenReturn(Optional.of(player));
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(adminOverrideService.clearOverrides(AdminOverrideTargetType.PLAYER, 10L)).thenReturn(2L);
        when(adminOverrideService.overrideInfos(AdminOverrideTargetType.PLAYER, 10L)).thenReturn(List.of());

        AdminDto.PlayerAdminResponse response = adminService.clearPlayerOverrides(1L, 10L);

        assertThat(response.getManualOverrides()).isEmpty();
        verify(adminOverrideService).clearOverrides(AdminOverrideTargetType.PLAYER, 10L);
        ArgumentCaptor<AdminAuditLog> logCaptor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(adminAuditLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getDetails()).isEqualTo("field=ALL; deletedCount=2");
    }

    @Test
    void clearOverrideRejectsUnknownFieldName() {
        assertThatThrownBy(() -> adminService.clearTeamOverride(1L, 47L, "unknown"))
                .isInstanceOf(CustomException.class);
    }

    private AppUser adminUser() {
        return AppUser.builder()
                .email("admin@example.com")
                .password("password")
                .nickname("Admin")
                .build();
    }
}
