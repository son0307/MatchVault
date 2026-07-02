package com.son.soccerStreaming.admin.controller;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.son.soccerStreaming.admin.service.AdminMediaService;
import com.son.soccerStreaming.admin.service.AdminService;
import com.son.soccerStreaming.auth.config.SecurityConfig;
import com.son.soccerStreaming.global.logging.RequestLoggingFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@Import({SecurityConfig.class, RequestLoggingFilter.class})
@ImportAutoConfiguration({
        SecurityAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class
})
class AdminMediaSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminService adminService;

    @MockitoBean
    private AdminMediaService adminMediaService;

    private Logger securityLogger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void attachLogAppender() {
        securityLogger = (Logger) LoggerFactory.getLogger(SecurityConfig.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        securityLogger.addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        securityLogger.detachAppender(logAppender);
        logAppender.stop();
    }

    @Test
    void rejectsUnauthenticatedMediaPresignRequest() throws Exception {
        mockMvc.perform(post("/api/v1/admin/media/uploads/presign")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(RequestLoggingFilter.REQUEST_ID_HEADER));

        assertThat(logAppender.list)
                .anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.WARN);
                    assertThat(event.getFormattedMessage()).contains("AUTHENTICATION_REQUIRED");
                    assertThat(event.getThrowableProxy()).isNull();
                    assertThat(keyValue(event, "event.code")).isEqualTo("AUTHENTICATION_REQUIRED");
                    assertThat(keyValue(event, "http.response.status_code")).isEqualTo(401);
                });
        verifyNoInteractions(adminMediaService);
    }

    @Test
    void rejectsNonAdminMediaRequests() throws Exception {
        mockMvc.perform(post("/api/v1/admin/media/uploads/presign")
                        .with(user("user@example.com").roles("USER"))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/media/uploads/complete")
                        .with(user("user@example.com").roles("USER"))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/admin/media/PLAYER_PHOTO/7")
                        .with(user("user@example.com").roles("USER")))
                .andExpect(status().isForbidden());

        assertThat(logAppender.list)
                .anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.WARN);
                    assertThat(event.getFormattedMessage()).contains("ACCESS_DENIED");
                    assertThat(event.getThrowableProxy()).isNull();
                    assertThat(keyValue(event, "event.code")).isEqualTo("ACCESS_DENIED");
                    assertThat(keyValue(event, "http.response.status_code")).isEqualTo(403);
                });
        verifyNoInteractions(adminMediaService);
    }

    private Object keyValue(ILoggingEvent event, String key) {
        return event.getKeyValuePairs().stream()
                .filter(pair -> pair.key.equals(key))
                .map(pair -> pair.value)
                .findFirst()
                .orElse(null);
    }
}
