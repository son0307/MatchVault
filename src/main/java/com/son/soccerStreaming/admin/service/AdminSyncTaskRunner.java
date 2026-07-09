package com.son.soccerStreaming.admin.service;

import com.son.soccerStreaming.admin.entity.AdminAuditLog;
import com.son.soccerStreaming.admin.entity.AdminAuditType;
import com.son.soccerStreaming.auth.entity.AppUser;
import com.son.soccerStreaming.global.exception.CustomException;
import com.son.soccerStreaming.global.exception.ErrorCode;
import com.son.soccerStreaming.admin.repository.AdminAuditLogRepository;
import com.son.soccerStreaming.auth.repository.AppUserRepository;
import com.son.soccerStreaming.apifootball.service.SyncProgressReporter;
import com.son.soccerStreaming.apifootball.service.SyncCancelledException;
import com.son.soccerStreaming.admin.entity.AdminSyncJobStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSyncTaskRunner {

    private final AppUserRepository appUserRepository;
    private final AdminAuditLogRepository adminAuditLogRepository;
    private final AdminSyncJobService adminSyncJobService;

    @Async("adminSyncTaskExecutor")
    public void run(Long jobId, Long adminUserId, String task, String targetType, Long targetId,
                    String details, SyncTask syncTask, Runnable afterCompletion) {
        AppUser admin = null;
        try {
            admin = findUser(adminUserId);
            if (!adminSyncJobService.markRunning(jobId)) {
                return;
            }
            SyncProgressReporter progressReporter = new AdminSyncJobProgressReporter(jobId, adminSyncJobService);
            progressReporter.checkCancelled();
            adminAuditLogRepository.save(AdminAuditLog.of(
                    admin,
                    AdminAuditType.SYNC,
                    targetType,
                    targetId,
                    task + " sync started in the background. " + details,
                    details,
                    true
            ));
            int count = syncTask.run(progressReporter);
            AdminSyncJobStatus status = adminSyncJobService.markSucceeded(jobId, count);
            if (status == AdminSyncJobStatus.CANCELLED) {
                String message = task + " sync cancelled. " + details;
                adminAuditLogRepository.save(AdminAuditLog.of(
                        admin, AdminAuditType.SYNC, targetType, targetId, message, details, true));
                return;
            }
            boolean success = status == AdminSyncJobStatus.SUCCEEDED;
            String message = success
                    ? task + " sync completed. " + details + "; count=" + count
                    : task + " sync completed with partial failures. " + details + "; count=" + count;
            adminAuditLogRepository.save(AdminAuditLog.of(admin, AdminAuditType.SYNC, targetType, targetId, message, details, success));
        } catch (SyncCancelledException exception) {
            adminSyncJobService.markCancelled(jobId);
            if (admin != null) {
                String message = task + " sync cancelled. " + details;
                adminAuditLogRepository.save(AdminAuditLog.of(
                        admin, AdminAuditType.SYNC, targetType, targetId, message, details, true));
            }
        } catch (Exception exception) {
            String message = task + " sync failed. " + details + ": " + exception.getMessage();
            try {
                adminSyncJobService.markFailed(jobId, message);
                if (admin != null) {
                    adminAuditLogRepository.save(AdminAuditLog.of(
                            admin, AdminAuditType.SYNC, targetType, targetId, message, details, false));
                }
            } catch (RuntimeException statusException) {
                log.error("Failed to persist admin sync failure status. jobId={}", jobId, statusException);
            }
            log.error("Admin background sync failed. task={}, targetType={}, targetId={}", task, targetType, targetId, exception);
        } finally {
            if (afterCompletion != null) {
                afterCompletion.run();
            }
        }
    }

    private AppUser findUser(Long adminUserId) {
        return appUserRepository.findById(adminUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    @FunctionalInterface
    public interface SyncTask {
        int run(SyncProgressReporter progressReporter);
    }
}
