package com.son.soccerStreaming.auth.service;

import com.son.soccerStreaming.auth.dto.AuthRequestDto;
import com.son.soccerStreaming.auth.dto.AuthResponseDto;
import com.son.soccerStreaming.auth.entity.AppUser;
import com.son.soccerStreaming.global.exception.CustomException;
import com.son.soccerStreaming.global.exception.ErrorCode;
import com.son.soccerStreaming.auth.repository.AppUserRepository;
import com.son.soccerStreaming.auth.security.AuthUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int EMAIL_MAX_LENGTH = 30;
    private static final int PASSWORD_MIN_LENGTH = 8;
    private static final int PASSWORD_MAX_BYTES = 72;
    private static final int NICKNAME_MIN_LENGTH = 2;
    private static final int NICKNAME_MAX_LENGTH = 20;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NICKNAME_PATTERN = Pattern.compile("^[가-힣A-Za-z0-9_]+$");

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponseDto.Me signup(AuthRequestDto.Signup request, HttpServletRequest servletRequest) {
        validateSignupRequest(request);
        String email = normalizeEmail(request.getEmail());
        String password = request.getPassword();
        String nickname = request.getNickname().trim();

        if (appUserRepository.existsByEmail(email)) {
            throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        try {
            appUserRepository.saveAndFlush(AppUser.builder()
                    .email(email)
                    .password(passwordEncoder.encode(password))
                    .nickname(nickname)
                    .build());
        } catch (DataIntegrityViolationException exception) {
            throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        return authenticate(email, password, servletRequest);
    }

    @Transactional(readOnly = true)
    public AuthResponseDto.Me login(AuthRequestDto.Login request, HttpServletRequest servletRequest) {
        validateLoginRequest(request);
        return authenticate(normalizeEmail(request.getEmail()), request.getPassword(), servletRequest);
    }

    public AuthResponseDto.Me me(AuthUserDetails userDetails) {
        if (userDetails == null) {
            throw new CustomException(ErrorCode.AUTHENTICATION_REQUIRED);
        }

        return toMe(userDetails);
    }

    private AuthResponseDto.Me authenticate(String email, String password, HttpServletRequest servletRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password)
            );

            // Persist the security context so signup/login immediately affects following same-origin API calls.
            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(authentication);
            SecurityContextHolder.setContext(securityContext);
            servletRequest.getSession(true).setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    securityContext
            );

            return toMe((AuthUserDetails) authentication.getPrincipal());
        } catch (BadCredentialsException exception) {
            throw new CustomException(ErrorCode.LOGIN_FAILED);
        }
    }

    private void validateSignupRequest(AuthRequestDto.Signup request) {
        if (request == null
                || isBlank(request.getEmail())
                || isBlank(request.getPassword())
                || isBlank(request.getNickname())) {
            throw new CustomException(ErrorCode.INVALID_AUTH_REQUEST);
        }

        String email = normalizeEmail(request.getEmail());
        String password = request.getPassword();
        String nickname = request.getNickname().trim();

        if (!isValidEmail(email)
                || !isValidPassword(password)
                || !isValidNickname(nickname)) {
            throw new CustomException(ErrorCode.INVALID_AUTH_REQUEST);
        }
    }

    private void validateLoginRequest(AuthRequestDto.Login request) {
        if (request == null || isBlank(request.getEmail()) || isBlank(request.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_AUTH_REQUEST);
        }
        String email = normalizeEmail(request.getEmail());
        if (!isValidEmail(email) || !isValidPassword(request.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_AUTH_REQUEST);
        }
    }

    private AuthResponseDto.Me toMe(AuthUserDetails userDetails) {
        return AuthResponseDto.Me.builder()
                .id(userDetails.getId())
                .email(userDetails.getEmail())
                .nickname(userDetails.getNickname())
                .role(userDetails.getRole())
                .build();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isValidEmail(String email) {
        return email.length() <= EMAIL_MAX_LENGTH
                && EMAIL_PATTERN.matcher(email).matches();
    }

    private boolean isValidPassword(String password) {
        return isValidPasswordLength(password)
                && password.length() >= PASSWORD_MIN_LENGTH;
    }

    private boolean isValidPasswordLength(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length <= PASSWORD_MAX_BYTES;
    }

    private boolean isValidNickname(String nickname) {
        return nickname.length() >= NICKNAME_MIN_LENGTH
                && nickname.length() <= NICKNAME_MAX_LENGTH
                && NICKNAME_PATTERN.matcher(nickname).matches();
    }
}
