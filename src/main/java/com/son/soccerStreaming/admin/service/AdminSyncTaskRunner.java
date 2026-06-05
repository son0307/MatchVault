package com.son.soccerStreaming.admin.service;

import com.son.soccerStreaming.admin.entity.AdminAuditLog;
import com.son.soccerStreaming.admin.entity.AdminAuditType;
import com.son.soccerStreaming.auth.entity.AppUser;
import com.son.soccerStreaming.global.exception.CustomException;
import com.son.soccerStreaming.global.exception.ErrorCode;
import com.son.soccerStreaming.admin.repository.AdminAuditLogRepository;
import com.son.soccerStreaming.auth.repository.AppUserRepository;
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

    @Async("adminSyncTaskExecutor")
    public void run(Long adminUserId, String task, String targetType, Long targetId, String details, SyncTask syncTask) {
        run(adminUserId, task, targetType, targetId, details, syncTask, null);
    }

    @Async("adminSyncTaskExecutor")
    public void run(Long adminUserId, String task, String targetType, Long targetId, String details, SyncTask syncTask, Runnable afterCompletion) {
        AppUser admin = findUser(adminUserId);
        adminAuditLogRepository.save(AdminAuditLog.of(
                admin,
                AdminAuditType.SYNC,
                targetType,
                targetId,
                task + " sync started in the background. " + details,
                details,
                true
        ));

        try {
            int count = syncTask.run();
            String message = task + " sync completed. " + details + "; count=" + count;
            adminAuditLogRepository.save(AdminAuditLog.of(admin, AdminAuditType.SYNC, targetType, targetId, message, details, true));
        } catch (Exception exception) {
            String message = task + " sync failed. " + details + ": " + exception.getMessage();
            adminAuditLogRepository.save(AdminAuditLog.of(admin, AdminAuditType.SYNC, targetType, targetId, message, details, false));
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
        int run();
    }
}
