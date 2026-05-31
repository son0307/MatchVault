package com.son.soccerStreaming.auth.service;

import com.son.soccerStreaming.auth.dto.AuthRequestDto;
import com.son.soccerStreaming.auth.dto.AuthResponseDto;
import com.son.soccerStreaming.auth.entity.AppUser;
import com.son.soccerStreaming.auth.repository.AppUserRepository;
import com.son.soccerStreaming.auth.security.AuthUserDetails;
import com.son.soccerStreaming.global.exception.CustomException;
import com.son.soccerStreaming.global.exception.ErrorCode;
import com.son.soccerStreaming.favorite.repository.FavoritePlayerRepository;
import com.son.soccerStreaming.favorite.repository.FavoriteTeamRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int EMAIL_MAX_LENGTH = 120;
    private static final int PASSWORD_MIN_LENGTH = 8;
    private static final int PASSWORD_MAX_LENGTH = 20;
    private static final int NICKNAME_MIN_LENGTH = 2;
    private static final int NICKNAME_MAX_LENGTH = 20;
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern NICKNAME_PATTERN = Pattern.compile("^[가-힣A-Za-z0-9_]+$");

    private final AppUserRepository appUserRepository;
    private final FavoriteTeamRepository favoriteTeamRepository;
    private final FavoritePlayerRepository favoritePlayerRepository;
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

        AppUser savedUser;
        try {
            savedUser = appUserRepository.saveAndFlush(AppUser.builder()
                    .email(email)
                    .password(passwordEncoder.encode(password))
                    .nickname(nickname)
                    .build());
        } catch (DataIntegrityViolationException exception) {
            throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        return authenticate(email, request.getPassword(), servletRequest);
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

    @Transactional
    public AuthResponseDto.Me updateNickname(
            AuthUserDetails userDetails,
            AuthRequestDto.UpdateNickname request,
            HttpServletRequest servletRequest
    ) {
        Long userId = authenticatedUserId(userDetails);
        if (request == null || isBlank(request.getNickname())) {
            throw new CustomException(ErrorCode.INVALID_AUTH_REQUEST);
        }
        validateNickname(request.getNickname());
        AppUser user = findUser(userId);
        user.updateNickname(request.getNickname().trim());
        refreshSecurityContext(user, servletRequest);
        return toMe(user);
    }

    @Transactional
    public void changePassword(AuthUserDetails userDetails, AuthRequestDto.ChangePassword request) {
        Long userId = authenticatedUserId(userDetails);
        if (request == null || isBlank(request.getCurrentPassword()) || isBlank(request.getNewPassword())) {
            throw new CustomException(ErrorCode.INVALID_AUTH_REQUEST);
        }
        validatePassword(request.getNewPassword());

        AppUser user = findUser(userId);
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_CURRENT_PASSWORD);
        }

        user.updatePassword(passwordEncoder.encode(request.getNewPassword()));
    }

    @Transactional
    public void deleteAccount(AuthUserDetails userDetails, AuthRequestDto.DeleteAccount request) {
        Long userId = authenticatedUserId(userDetails);
        if (request == null || isBlank(request.getCurrentPassword())) {
            throw new CustomException(ErrorCode.INVALID_AUTH_REQUEST);
        }

        AppUser user = findUser(userId);
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_CURRENT_PASSWORD);
        }

        favoritePlayerRepository.deleteByUserId(userId);
        favoriteTeamRepository.deleteByUserId(userId);
        appUserRepository.delete(user);
    }

    private AuthResponseDto.Me authenticate(String email, String password, HttpServletRequest servletRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password)
            );

            return storeAuthentication((AuthUserDetails) authentication.getPrincipal(), servletRequest);
        } catch (BadCredentialsException exception) {
            throw new CustomException(ErrorCode.LOGIN_FAILED);
        }
    }

    private AuthResponseDto.Me storeAuthentication(AuthUserDetails userDetails, HttpServletRequest servletRequest) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );

        try {
            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(authentication);
            SecurityContextHolder.setContext(securityContext);
            servletRequest.getSession(true).setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    securityContext
            );

            return toMe(userDetails);
        } catch (RuntimeException exception) {
            SecurityContextHolder.clearContext();
            throw exception;
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
        validateNickname(request.getNickname());
    }

    private void validateLoginRequest(AuthRequestDto.Login request) {
        if (request == null || isBlank(request.getEmail()) || isBlank(request.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_AUTH_REQUEST);
        }
        String email = normalizeEmail(request.getEmail());
        if (!isValidEmail(email)) {
            throw new CustomException(ErrorCode.INVALID_AUTH_REQUEST);
        }
    }

    private void validatePassword(String password) {
        if (isBlank(password)) {
            throw new CustomException(ErrorCode.INVALID_AUTH_REQUEST);
        }
        String trimmed = password.trim();
        if (trimmed.length() < PASSWORD_MIN_LENGTH || trimmed.length() > PASSWORD_MAX_LENGTH) {
            throw new CustomException(ErrorCode.INVALID_AUTH_REQUEST);
        }
    }

    private void validateNickname(String nickname) {
        if (isBlank(nickname)) {
            throw new CustomException(ErrorCode.INVALID_AUTH_REQUEST);
        }
        int length = nickname.trim().length();
        if (length < NICKNAME_MIN_LENGTH || length > NICKNAME_MAX_LENGTH) {
            throw new CustomException(ErrorCode.INVALID_AUTH_REQUEST);
        }
    }

    private Long authenticatedUserId(AuthUserDetails userDetails) {
        if (userDetails == null) {
            throw new CustomException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
        return userDetails.getId();
    }

    private AppUser findUser(Long userId) {
        return appUserRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private AuthResponseDto.Me toMe(AuthUserDetails userDetails) {
        return AuthResponseDto.Me.builder()
                .id(userDetails.getId())
                .email(userDetails.getEmail())
                .nickname(userDetails.getNickname())
                .role(userDetails.getRole())
                .build();
    }

    private AuthResponseDto.Me toMe(AppUser user) {
        return AuthResponseDto.Me.builder()
                .id(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .role(user.roleOrDefault().name())
                .build();
    }

    private void refreshSecurityContext(AppUser user, HttpServletRequest servletRequest) {
        AuthUserDetails principal = new AuthUserDetails(user);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
        );
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);
        servletRequest.getSession(true).setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                securityContext
        );
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
        return password.length() >= PASSWORD_MIN_LENGTH
                && password.length() <= PASSWORD_MAX_LENGTH;
    }

    private boolean isValidNickname(String nickname) {
        return nickname.length() >= NICKNAME_MIN_LENGTH
                && nickname.length() <= NICKNAME_MAX_LENGTH
                && NICKNAME_PATTERN.matcher(nickname).matches();
    }
}
