package com.son.soccerStreaming.service;

import com.son.soccerStreaming.entity.AdminAuditLog;
import com.son.soccerStreaming.entity.AdminAuditType;
import com.son.soccerStreaming.entity.AppUser;
import com.son.soccerStreaming.exception.CustomException;
import com.son.soccerStreaming.exception.ErrorCode;
import com.son.soccerStreaming.repository.AdminAuditLogRepository;
import com.son.soccerStreaming.repository.AppUserRepository;
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
    public void run(Long adminUserId, String task, String targetType, Long targetId, SyncTask syncTask) {
        AppUser admin = findUser(adminUserId);
        adminAuditLogRepository.save(AdminAuditLog.of(
                admin,
                AdminAuditType.SYNC,
                targetType,
                targetId,
                task + " sync started in the background.",
                true
        ));

        try {
            int count = syncTask.run();
            String message = task + " sync completed. count=" + count;
            adminAuditLogRepository.save(AdminAuditLog.of(admin, AdminAuditType.SYNC, targetType, targetId, message, true));
        } catch (Exception exception) {
            String message = task + " sync failed: " + exception.getMessage();
            adminAuditLogRepository.save(AdminAuditLog.of(admin, AdminAuditType.SYNC, targetType, targetId, message, false));
            log.error("Admin background sync failed. task={}, targetType={}, targetId={}", task, targetType, targetId, exception);
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
