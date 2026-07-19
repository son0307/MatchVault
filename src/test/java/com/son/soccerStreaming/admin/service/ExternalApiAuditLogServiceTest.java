package com.son.soccerStreaming.admin.service;

import com.son.soccerStreaming.admin.entity.AdminAuditLog;
import com.son.soccerStreaming.admin.entity.AdminAuditType;
import com.son.soccerStreaming.admin.repository.AdminAuditLogRepository;
import com.son.soccerStreaming.auth.entity.AppUser;
import com.son.soccerStreaming.auth.repository.AppUserRepository;
import com.son.soccerStreaming.global.externalapi.ExternalApiCallCompletedEvent;
import com.son.soccerStreaming.global.externalapi.ExternalApiErrorCategory;
import com.son.soccerStreaming.global.externalapi.ExternalApiInvocationContext;
import com.son.soccerStreaming.global.externalapi.ExternalApiProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExternalApiAuditLogServiceTest {

    @Test
    void storesOneSanitizedAuditForAnAdminCall() {
        AppUserRepository userRepository = mock(AppUserRepository.class);
        AdminAuditLogRepository auditRepository = mock(AdminAuditLogRepository.class);
        AppUser admin = AppUser.builder().id(9L).email("admin@example.com").password("x").nickname("admin").build();
        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
        ExternalApiAuditLogService service = new ExternalApiAuditLogService(userRepository, auditRepository);

        service.record(new ExternalApiCallCompletedEvent(
                ExternalApiProvider.OPENAI, "translateNewsTitles",
                ExternalApiInvocationContext.admin(9L, 42L, 7L, 1), false, 3, 1250L,
                null, 503, ExternalApiErrorCategory.UPSTREAM_SERVER));

        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(auditRepository).save(captor.capture());
        AdminAuditLog log = captor.getValue();
        assertThat(log.getType()).isEqualTo(AdminAuditType.EXTERNAL_API_CALL);
        assertThat(log.getProvider()).isEqualTo("OPENAI");
        assertThat(log.isSuccess()).isFalse();
        assertThat(log.getDetails()).contains("teamId=42", "articleId=7", "attempts=3", "httpStatus=503")
                .doesNotContain("api_key", "Authorization", "Original title");
    }

    @Test
    void ignoresSystemCalls() {
        AppUserRepository userRepository = mock(AppUserRepository.class);
        AdminAuditLogRepository auditRepository = mock(AdminAuditLogRepository.class);
        ExternalApiAuditLogService service = new ExternalApiAuditLogService(userRepository, auditRepository);

        service.record(new ExternalApiCallCompletedEvent(
                ExternalApiProvider.SERP_API, "searchTeamNews", ExternalApiInvocationContext.system(),
                true, 1, 10L, 2, null, null));

        verify(auditRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
