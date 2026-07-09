package com.son.soccerStreaming.admin.service;

import com.son.soccerStreaming.admin.entity.AdminSyncJob;
import com.son.soccerStreaming.admin.entity.AdminSyncJobStatus;
import com.son.soccerStreaming.admin.repository.AdminSyncJobErrorRepository;
import com.son.soccerStreaming.admin.repository.AdminSyncJobRepository;
import com.son.soccerStreaming.auth.entity.AppUser;
import com.son.soccerStreaming.auth.repository.AppUserRepository;
import com.son.soccerStreaming.global.exception.CustomException;
import com.son.soccerStreaming.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminSyncJobServiceTest {

    @Mock private AdminSyncJobRepository jobRepository;
    @Mock private AdminSyncJobErrorRepository errorRepository;
    @Mock private AppUserRepository appUserRepository;
    @InjectMocks private AdminSyncJobService service;

    @Test
    void createsAndCompletesPersistentJob() {
        AppUser admin = AppUser.builder().email("admin@example.com").build();
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(jobRepository.save(org.mockito.ArgumentMatchers.any(AdminSyncJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AdminSyncJob job = service.create(1L, "players", "PLAYER", null, 2025, "league=39; season=2025");
        job.markRunning();
        job.updateProgress(20, "teams", 10, 10, 0, 250);
        job.markSucceeded(500, false);

        assertThat(job.getStatus()).isEqualTo(AdminSyncJobStatus.SUCCEEDED);
        assertThat(job.getSavedCount()).isEqualTo(500);
        assertThat(job.getCompletedAt()).isNotNull();
    }

    @Test
    void failureAfterSuccessfulUnitsBecomesPartialFailure() {
        AdminSyncJob job = AdminSyncJob.queued(AppUser.builder().email("admin@example.com").build(),
                "players", "PLAYER", null, 2025, "season=2025");
        job.markRunning();
        job.updateProgress(20, "teams", 8, 7, 1, 175);

        job.markFailed("one team failed");

        assertThat(job.getStatus()).isEqualTo(AdminSyncJobStatus.PARTIAL_FAILED);
    }

    @Test
    void recoversActiveJobsAsFailedAfterRestart() {
        AdminSyncJob queued = AdminSyncJob.queued(AppUser.builder().email("admin@example.com").build(),
                "injuries", "INJURY", null, 2025, "season=2025");
        when(jobRepository.findAllByStatusIn(List.of(
                AdminSyncJobStatus.QUEUED, AdminSyncJobStatus.RUNNING, AdminSyncJobStatus.CANCEL_REQUESTED)))
                .thenReturn(List.of(queued));

        service.recoverInterruptedJobs();

        assertThat(queued.getStatus()).isEqualTo(AdminSyncJobStatus.FAILED);
        assertThat(queued.getMessage()).contains("application restarted");
    }

    @Test
    void recentJobsClampsLimitAndReturnsErrors() {
        AppUser admin = AppUser.builder().email("admin@example.com").build();
        AdminSyncJob job = AdminSyncJob.builder()
                .id(7L)
                .adminUser(admin)
                .task("players")
                .status(AdminSyncJobStatus.SUCCEEDED)
                .build();
        when(jobRepository.findAllByStatusInOrderByCreatedAtDesc(
                List.of(AdminSyncJobStatus.QUEUED, AdminSyncJobStatus.RUNNING, AdminSyncJobStatus.CANCEL_REQUESTED)))
                .thenReturn(List.of());
        when(jobRepository.findAllByStatusNotInOrderByCompletedAtDesc(
                List.of(AdminSyncJobStatus.QUEUED, AdminSyncJobStatus.RUNNING, AdminSyncJobStatus.CANCEL_REQUESTED),
                PageRequest.of(0, 50))).thenReturn(List.of(job));
        when(errorRepository.findAllByJobIdInOrderByIdAsc(List.of(7L))).thenReturn(List.of());

        var response = service.recentJobs(100);

        assertThat(response.getJobs()).extracting(com.son.soccerStreaming.admin.dto.AdminDto.SyncJobResponse::getId)
                .containsExactly(7L);
        verify(jobRepository).findAllByStatusNotInOrderByCompletedAtDesc(
                List.of(AdminSyncJobStatus.QUEUED, AdminSyncJobStatus.RUNNING, AdminSyncJobStatus.CANCEL_REQUESTED),
                PageRequest.of(0, 50));
    }

    @Test
    void queuedJobCancelsImmediatelyAndRunningJobRequestsCancellation() {
        AdminSyncJob queued = AdminSyncJob.queued(AppUser.builder().email("admin@example.com").build(),
                "players", "PLAYER", null, 2025, "season=2025");
        AdminSyncJob running = AdminSyncJob.queued(AppUser.builder().email("admin@example.com").build(),
                "injuries", "INJURY", null, 2025, "season=2025");
        running.markRunning();

        assertThat(queued.requestCancel()).isTrue();
        assertThat(queued.getStatus()).isEqualTo(AdminSyncJobStatus.CANCELLED);
        assertThat(running.requestCancel()).isFalse();
        assertThat(running.getStatus()).isEqualTo(AdminSyncJobStatus.CANCEL_REQUESTED);
        assertThat(running.isCancellationRequested()).isTrue();
    }

    @Test
    void terminalJobStatusIsNotOverwrittenByLateFailureOrCancellation() {
        AdminSyncJob job = AdminSyncJob.queued(AppUser.builder().email("admin@example.com").build(),
                "players", "PLAYER", null, 2025, "season=2025");
        job.markRunning();
        job.markSucceeded(10, false);
        LocalDateTime completedAt = job.getCompletedAt();

        job.markFailed("late audit failure");
        job.markCancelled();
        job.requestCancel();

        assertThat(job.getStatus()).isEqualTo(AdminSyncJobStatus.SUCCEEDED);
        assertThat(job.getCompletedAt()).isEqualTo(completedAt);
    }

    @Test
    void cancellationRequestedJobCanOnlyFinishAsCancelled() {
        AdminSyncJob job = AdminSyncJob.queued(AppUser.builder().email("admin@example.com").build(),
                "fixture-details", "FIXTURE", null, 2025, "season=2025");
        job.markRunning();
        job.requestCancel();

        job.markSucceeded(10, false);

        assertThat(job.getStatus()).isEqualTo(AdminSyncJobStatus.CANCELLED);
    }

    @Test
    void missingJobUsesNotFoundErrorInsteadOfInternalServerError() {
        when(jobRepository.findByIdForUpdate(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requestCancel(404L))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ADMIN_SYNC_JOB_NOT_FOUND));
    }

    @Test
    void ordersRunningThenQueuedFifoThenRecentCompleted() {
        AppUser admin = AppUser.builder().email("admin@example.com").build();
        LocalDateTime now = LocalDateTime.now();
        AdminSyncJob queuedNew = job(2L, admin, AdminSyncJobStatus.QUEUED, now.minusMinutes(1));
        AdminSyncJob running = job(3L, admin, AdminSyncJobStatus.RUNNING, now.minusMinutes(3));
        AdminSyncJob queuedOld = job(1L, admin, AdminSyncJobStatus.QUEUED, now.minusMinutes(2));
        AdminSyncJob completed = job(4L, admin, AdminSyncJobStatus.SUCCEEDED, now.minusMinutes(4));
        List<AdminSyncJobStatus> active = List.of(
                AdminSyncJobStatus.QUEUED, AdminSyncJobStatus.RUNNING, AdminSyncJobStatus.CANCEL_REQUESTED);
        when(jobRepository.findAllByStatusInOrderByCreatedAtDesc(active))
                .thenReturn(List.of(queuedNew, running, queuedOld));
        when(jobRepository.findAllByStatusNotInOrderByCompletedAtDesc(active, PageRequest.of(0, 10)))
                .thenReturn(List.of(completed));
        when(errorRepository.findAllByJobIdInOrderByIdAsc(List.of(3L, 1L, 2L, 4L))).thenReturn(List.of());

        var response = service.recentJobs(10);

        assertThat(response.getJobs()).extracting(com.son.soccerStreaming.admin.dto.AdminDto.SyncJobResponse::getId)
                .containsExactly(3L, 1L, 2L, 4L);
    }

    private AdminSyncJob job(Long id, AppUser admin, AdminSyncJobStatus status, LocalDateTime createdAt) {
        return AdminSyncJob.builder()
                .id(id)
                .adminUser(admin)
                .task("players")
                .status(status)
                .createdAt(createdAt)
                .build();
    }
}
