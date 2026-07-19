package com.son.soccerStreaming.admin.service;

import com.son.soccerStreaming.global.externalapi.ExternalApiCallCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalApiAuditLogListener {
    private final ExternalApiAuditLogService auditLogService;

    @EventListener
    public void onCompleted(ExternalApiCallCompletedEvent event) {
        if (event.context() == null || !event.context().isAdminRequest()) return;
        try {
            auditLogService.record(event);
        } catch (RuntimeException exception) {
            log.error("Failed to persist external API admin audit. provider={}, operation={}",
                    event.provider(), event.operation(), exception);
        }
    }
}
