package com.son.soccerStreaming.admin.service;

import com.son.soccerStreaming.admin.dto.AdminDto;
import com.son.soccerStreaming.admin.entity.AdminSyncJob;
import com.son.soccerStreaming.admin.entity.AdminSyncJobError;
import com.son.soccerStreaming.admin.entity.AdminSyncJobStatus;
import com.son.soccerStreaming.admin.repository.AdminSyncJobErrorRepository;
import com.son.soccerStreaming.admin.repository.AdminSyncJobRepository;
import com.son.soccerStreaming.auth.entity.AppUser;
import com.son.soccerStreaming.auth.repository.AppUserRepository;
import com.son.soccerStreaming.global.exception.CustomException;
import com.son.soccerStreaming.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminSyncJobService {

    private static final List<AdminSyncJobStatus> ACTIVE_STATUSES = List.of(
            AdminSyncJobStatus.QUEUED,
            AdminSyncJobStatus.RUNNING,
            AdminSyncJobStatus.CANCEL_REQUESTED
    );

    private final AdminSyncJobRepository jobRepository;
    private final AdminSyncJobErrorRepository errorRepository;
    private final AppUserRepository appUserRepository;

    @Transactional
    public AdminSyncJob create(Long adminUserId, String task, String targetType, Long targetId,
                               Integer season, String details) {
        AppUser admin = appUserRepository.findById(adminUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return jobRepository.save(AdminSyncJob.queued(admin, task, targetType, targetId, season, details));
    }

    @Transactional(readOnly = true)
    public boolean hasActiveJob(String task, String details) {
        return jobRepository.existsByTaskAndDetailsAndStatusIn(task, details, ACTIVE_STATUSES);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markRunning(Long jobId) {
        return job(jobId).markRunning();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void beginPhase(Long jobId, String phase, int totalUnits, String unitLabel, int savedCount) {
        job(jobId).beginPhase(safe(phase, 50), totalUnits, unitLabel, savedCount);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateProgress(Long jobId, int totalUnits, String unitLabel, int processedUnits,
                               int successfulUnits, int failedUnits, int savedCount) {
        job(jobId).updateProgress(totalUnits, unitLabel, processedUnits, successfulUnits, failedUnits, savedCount);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void addError(Long jobId, String unitType, String unitId, String message) {
        errorRepository.save(AdminSyncJobError.of(job(jobId), safe(unitType, 30), safe(unitId, 200), safe(message, 1000)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AdminSyncJobStatus markSucceeded(Long jobId, int count) {
        AdminSyncJob job = job(jobId);
        job.markSucceeded(count, errorRepository.existsByJobId(jobId));
        return job.getStatus();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long jobId, String message) {
        job(jobId).markFailed(safe(message, 1000));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCancelled(Long jobId) {
        job(jobId).markCancelled();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean isCancellationRequested(Long jobId) {
        return job(jobId).isCancellationRequested();
    }

    @Transactional
    public CancelResult requestCancel(Long jobId) {
        AdminSyncJob job = job(jobId);
        boolean cancelledBeforeStart = job.requestCancel();
        return new CancelResult(job.getId(), job.getTask(), job.getDetails(), job.getStatus(), cancelledBeforeStart);
    }

    @Transactional(readOnly = true)
    public AdminDto.SyncJobListResponse recentJobs(Integer limit) {
        int safeLimit = limit == null ? 10 : Math.min(Math.max(limit, 1), 50);
        List<AdminSyncJob> activeJobs = new ArrayList<>(
                jobRepository.findAllByStatusInOrderByCreatedAtDesc(ACTIVE_STATUSES));
        activeJobs.sort(Comparator
                .comparingInt((AdminSyncJob job) -> activeRank(job.getStatus()))
                .thenComparing(AdminSyncJob::getCreatedAt));
        List<AdminSyncJob> jobs = new ArrayList<>(activeJobs);
        jobs.addAll(jobRepository.findAllByStatusNotInOrderByCompletedAtDesc(
                ACTIVE_STATUSES, PageRequest.of(0, safeLimit)));
        List<Long> ids = jobs.stream().map(AdminSyncJob::getId).toList();
        Map<Long, List<AdminSyncJobError>> errorsByJob = ids.isEmpty()
                ? Map.of()
                : errorRepository.findAllByJobIdInOrderByIdAsc(ids).stream()
                .collect(Collectors.groupingBy(error -> error.getJob().getId()));
        return AdminDto.SyncJobListResponse.builder()
                .jobs(jobs.stream().map(job -> toResponse(job, errorsByJob.getOrDefault(job.getId(), List.of()))).toList())
                .build();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverInterruptedJobs() {
        jobRepository.findAllByStatusIn(List.of(
                        AdminSyncJobStatus.QUEUED, AdminSyncJobStatus.RUNNING, AdminSyncJobStatus.CANCEL_REQUESTED))
                .forEach(job -> {
                    if (job.getStatus() == AdminSyncJobStatus.CANCEL_REQUESTED) {
                        job.markCancelled();
                    } else {
                        job.markFailed("Sync was interrupted because the application restarted.");
                    }
                });
    }

    private AdminSyncJob job(Long jobId) {
        return jobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new CustomException(ErrorCode.ADMIN_SYNC_JOB_NOT_FOUND));
    }

    private AdminDto.SyncJobResponse toResponse(AdminSyncJob job, List<AdminSyncJobError> errors) {
        return AdminDto.SyncJobResponse.builder()
                .id(job.getId())
                .task(job.getTask())
                .adminEmail(job.getAdminUser().getEmail())
                .targetType(job.getTargetType())
                .targetId(job.getTargetId())
                .season(job.getSeason())
                .details(job.getDetails())
                .status(job.getStatus().name())
                .active(job.getStatus().isActive())
                .totalUnits(job.getTotalUnits())
                .processedUnits(job.getProcessedUnits())
                .successfulUnits(job.getSuccessfulUnits())
                .failedUnits(job.getFailedUnits())
                .savedCount(job.getSavedCount())
                .phase(job.getPhase())
                .unitLabel(job.getUnitLabel())
                .message(publicStatusMessage(job.getStatus()))
                .createdAt(job.getCreatedAt())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .errors(errors.stream().map(error -> AdminDto.SyncJobErrorResponse.builder()
                        .unitType(error.getUnitType())
                        .unitId(error.getUnitId())
                        .message("세부 오류는 서버 로그에서 확인해 주세요.")
                        .createdAt(error.getCreatedAt())
                        .build()).toList())
                .build();
    }

    private int activeRank(AdminSyncJobStatus status) {
        if (status == AdminSyncJobStatus.RUNNING || status == AdminSyncJobStatus.CANCEL_REQUESTED) {
            return 0;
        }
        return 1;
    }

    private String publicStatusMessage(AdminSyncJobStatus status) {
        return switch (status) {
            case QUEUED -> "동기화 작업이 대기 중입니다.";
            case RUNNING -> "동기화 작업을 진행하고 있습니다.";
            case CANCEL_REQUESTED -> "현재 처리 단위를 마친 뒤 취소합니다.";
            case CANCELLED -> "관리자 요청으로 취소되었습니다.";
            case SUCCEEDED -> "성공적으로 동기화되었습니다.";
            case PARTIAL_FAILED -> "일부 데이터는 동기화하지 못했습니다.";
            case FAILED -> "동기화에 실패했습니다.";
        };
    }

    private String safe(String value, int maxLength) {
        String result = value == null || value.isBlank() ? "Unknown error" : value.replaceAll("[\\r\\n\\t]", " ");
        return result.length() <= maxLength ? result : result.substring(0, maxLength);
    }

    public record CancelResult(Long jobId, String task, String details, AdminSyncJobStatus status,
                               boolean cancelledBeforeStart) {
    }
}
