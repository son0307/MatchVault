package com.son.soccerStreaming.admin.controller;

import com.son.soccerStreaming.admin.service.AdminMediaService;
import com.son.soccerStreaming.admin.service.AdminService;
import com.son.soccerStreaming.auth.config.SecurityConfig;
import org.junit.jupiter.api.Test;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
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

    @Test
    void rejectsUnauthenticatedMediaPresignRequest() throws Exception {
        mockMvc.perform(post("/api/v1/admin/media/uploads/presign")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isUnauthorized());

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

        verifyNoInteractions(adminMediaService);
    }
}
