package com.son.soccerStreaming.service;

import com.son.soccerStreaming.apifootball.service.ApiFootballFixtureDetailSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballFixtureSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballInjurySyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballPlayerSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballStandingSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballTeamSyncService;
import com.son.soccerStreaming.dto.AdminDto;
import com.son.soccerStreaming.entity.AdminAuditLog;
import com.son.soccerStreaming.entity.AdminOverrideTargetType;
import com.son.soccerStreaming.entity.AppUser;
import com.son.soccerStreaming.entity.Team;
import com.son.soccerStreaming.repository.AdminAuditLogRepository;
import com.son.soccerStreaming.repository.AppUserRepository;
import com.son.soccerStreaming.repository.PlayerRepository;
import com.son.soccerStreaming.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
}
