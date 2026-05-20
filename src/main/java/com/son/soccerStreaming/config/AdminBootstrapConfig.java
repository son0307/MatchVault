package com.son.soccerStreaming.config;

import com.son.soccerStreaming.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrapConfig {

    private final AppUserRepository appUserRepository;

    @Value("${admin.emails:${ADMIN_EMAILS:}}")
    private String adminEmails;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void promoteConfiguredAdmins() {
        if (adminEmails == null || adminEmails.isBlank()) {
            return;
        }

        Arrays.stream(adminEmails.split(","))
                .map(String::trim)
                .filter(email -> !email.isBlank())
                .map(String::toLowerCase)
                .forEach(email -> appUserRepository.findByEmail(email).ifPresent(user -> {
                    user.promoteToAdmin();
                    log.info("Admin user promoted by ADMIN_EMAILS. email={}", email);
                }));
    }
}
