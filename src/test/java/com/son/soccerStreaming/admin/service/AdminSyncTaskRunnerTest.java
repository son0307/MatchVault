package com.son.soccerStreaming.admin.service;

import com.son.soccerStreaming.admin.entity.AdminAuditLog;
import com.son.soccerStreaming.admin.repository.AdminAuditLogRepository;
import com.son.soccerStreaming.admin.entity.AdminSyncJobStatus;
import com.son.soccerStreaming.auth.entity.AppUser;
import com.son.soccerStreaming.auth.repository.AppUserRepository;
import com.son.soccerStreaming.apifootball.service.SyncCancelledException;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.assertj.core.api.Assertions.assertThat;

class AdminSyncTaskRunnerTest {

    @Test
    void recordsSuccessfulCompletionAndReleasesManualSync() {
        AppUserRepository userRepository = mock(AppUserRepository.class);
        AdminAuditLogRepository auditRepository = mock(AdminAuditLogRepository.class);
        AdminSyncJobService jobService = mock(AdminSyncJobService.class);
        AppUser admin = AppUser.builder().email("admin@example.com").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        AtomicBoolean released = new AtomicBoolean();
        AdminSyncTaskRunner runner = new AdminSyncTaskRunner(userRepository, auditRepository, jobService);
        when(jobService.markRunning(10L)).thenReturn(true);
        when(jobService.markSucceeded(10L, 42)).thenReturn(AdminSyncJobStatus.SUCCEEDED);

        runner.run(10L, 1L, "players", "PLAYER", null, "season=2025",
                progress -> 42, () -> released.set(true));

        verify(jobService).markRunning(10L);
        verify(jobService).markSucceeded(10L, 42);
        verify(auditRepository, org.mockito.Mockito.times(2)).save(any(AdminAuditLog.class));
        assertThat(released.get()).isTrue();
    }

    @Test
    void recordsFailureAndReleasesManualSync() {
        AppUserRepository userRepository = mock(AppUserRepository.class);
        AdminAuditLogRepository auditRepository = mock(AdminAuditLogRepository.class);
        AdminSyncJobService jobService = mock(AdminSyncJobService.class);
        when(userRepository.findById(1L)).thenReturn(Optional.of(AppUser.builder().email("admin@example.com").build()));
        AtomicBoolean released = new AtomicBoolean();
        AdminSyncTaskRunner runner = new AdminSyncTaskRunner(userRepository, auditRepository, jobService);
        when(jobService.markRunning(11L)).thenReturn(true);

        runner.run(11L, 1L, "injuries", "INJURY", null, "season=2025",
                progress -> { throw new IllegalStateException("upstream failed"); },
                () -> released.set(true));

        verify(jobService).markFailed(11L, "injuries sync failed. season=2025: upstream failed");
        assertThat(released.get()).isTrue();
    }

    @Test
    void marksCooperativelyCancelledTaskWithoutFailure() {
        AppUserRepository userRepository = mock(AppUserRepository.class);
        AdminAuditLogRepository auditRepository = mock(AdminAuditLogRepository.class);
        AdminSyncJobService jobService = mock(AdminSyncJobService.class);
        when(userRepository.findById(1L)).thenReturn(Optional.of(AppUser.builder().email("admin@example.com").build()));
        when(jobService.markRunning(12L)).thenReturn(true);
        AdminSyncTaskRunner runner = new AdminSyncTaskRunner(userRepository, auditRepository, jobService);

        runner.run(12L, 1L, "players", "PLAYER", null, "season=2025",
                progress -> { throw new SyncCancelledException(); }, null);

        verify(jobService).markCancelled(12L);
        verify(jobService, never()).markFailed(any(), any());
    }

    @Test
    void stopsBeforeAuditAndSyncWhenCancelledImmediatelyAfterMarkRunning() {
        AppUserRepository userRepository = mock(AppUserRepository.class);
        AdminAuditLogRepository auditRepository = mock(AdminAuditLogRepository.class);
        AdminSyncJobService jobService = mock(AdminSyncJobService.class);
        when(userRepository.findById(1L)).thenReturn(Optional.of(AppUser.builder().email("admin@example.com").build()));
        when(jobService.markRunning(13L)).thenReturn(true);
        when(jobService.isCancellationRequested(13L)).thenReturn(true);
        AdminSyncTaskRunner runner = new AdminSyncTaskRunner(userRepository, auditRepository, jobService);
        AtomicBoolean syncStarted = new AtomicBoolean();

        runner.run(13L, 1L, "players", "PLAYER", null, "season=2025",
                progress -> {
                    syncStarted.set(true);
                    return 0;
                }, null);

        assertThat(syncStarted.get()).isFalse();
        verify(jobService).markCancelled(13L);
        verify(jobService, never()).markSucceeded(any(), anyInt());
        verify(jobService, never()).markFailed(any(), any());
    }
}
