package com.son.soccerStreaming.auth.config;

import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Same-origin static UI calls JSON APIs, so the first auth iteration keeps CSRF off.
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin", "/admin/**", "/admin.html", "/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/auth/me", "/api/v1/auth/me/**", "/api/v1/favorites/**").authenticated()
                        .anyRequest().permitAll()
                )
                .exceptionHandling(exception -> exception
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            log.atWarn()
                                    .addKeyValue("event.action", "http-request-rejected")
                                    .addKeyValue("event.outcome", "failure")
                                    .addKeyValue("event.code", "ACCESS_DENIED")
                                    .addKeyValue("http.response.status_code", HttpStatus.FORBIDDEN.value())
                                    .log("Client request rejected by security. status={}, error={}, method={}, uri={}",
                                            HttpStatus.FORBIDDEN.value(), "ACCESS_DENIED",
                                            request.getMethod(), request.getRequestURI());
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            objectMapper.writeValue(response.getWriter(), Map.of(
                                    "status", HttpStatus.FORBIDDEN.value(),
                                    "error", "ACCESS_DENIED",
                                    "message", "관리자 권한이 필요합니다."
                            ));
                        })
                        .authenticationEntryPoint((request, response, authException) -> {
                            log.atWarn()
                                    .addKeyValue("event.action", "http-request-rejected")
                                    .addKeyValue("event.outcome", "failure")
                                    .addKeyValue("event.code", "AUTHENTICATION_REQUIRED")
                                    .addKeyValue("http.response.status_code", HttpStatus.UNAUTHORIZED.value())
                                    .log("Client request rejected by security. status={}, error={}, method={}, uri={}",
                                            HttpStatus.UNAUTHORIZED.value(), "AUTHENTICATION_REQUIRED",
                                            request.getMethod(), request.getRequestURI());
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            objectMapper.writeValue(response.getWriter(), Map.of(
                                    "status", HttpStatus.UNAUTHORIZED.value(),
                                    "error", "AUTHENTICATION_REQUIRED",
                                    "message", "로그인이 필요합니다."
                            ));
                        })
                );

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
