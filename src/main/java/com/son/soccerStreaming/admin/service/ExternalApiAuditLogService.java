package com.son.soccerStreaming.admin.service;

import com.son.soccerStreaming.admin.entity.AdminAuditLog;
import com.son.soccerStreaming.admin.repository.AdminAuditLogRepository;
import com.son.soccerStreaming.auth.repository.AppUserRepository;
import com.son.soccerStreaming.global.externalapi.ExternalApiCallCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExternalApiAuditLogService {
    private final AppUserRepository appUserRepository;
    private final AdminAuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(ExternalApiCallCompletedEvent event) {
        var context = event.context();
        if (context == null || !context.isAdminRequest()) return;
        var admin = appUserRepository.findById(context.adminUserId()).orElse(null);
        if (admin == null) return;

        List<String> details = new ArrayList<>();
        details.add("operation=" + event.operation());
        if (context.teamId() != null) details.add("teamId=" + context.teamId());
        if (context.articleId() != null) details.add("articleId=" + context.articleId());
        if (context.batchSize() != null) details.add("batchSize=" + context.batchSize());
        if (event.resultCount() != null) details.add("resultCount=" + event.resultCount());
        details.add("attempts=" + event.attempts());
        details.add("durationMs=" + event.durationMs());
        if (event.httpStatus() != null) details.add("httpStatus=" + event.httpStatus());
        if (event.errorCategory() != null) details.add("errorCategory=" + event.errorCategory().name());

        String message = event.provider().displayName() + " " + event.operation()
                + (event.success() ? " call completed" : " call failed");
        auditLogRepository.save(AdminAuditLog.externalApiCall(
                admin, event.provider().name(), message, String.join("; ", details), event.success()));
    }
}
