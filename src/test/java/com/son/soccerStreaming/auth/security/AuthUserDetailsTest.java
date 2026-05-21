package com.son.soccerStreaming.auth.security;

import com.son.soccerStreaming.auth.entity.AppUser;
import com.son.soccerStreaming.auth.entity.UserRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthUserDetailsTest {

    @Test
    void adminUserHasAdminAuthority() {
        AppUser user = AppUser.builder()
                .email("admin@example.com")
                .password("password")
                .nickname("Admin")
                .role(UserRole.ADMIN)
                .build();

        AuthUserDetails userDetails = new AuthUserDetails(user);

        assertThat(userDetails.getRole()).isEqualTo("ADMIN");
        assertThat(userDetails.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN");
    }
}
