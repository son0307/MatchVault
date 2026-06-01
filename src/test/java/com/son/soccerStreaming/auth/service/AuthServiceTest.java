package com.son.soccerStreaming.auth.service;

import com.son.soccerStreaming.auth.dto.AuthRequestDto;
import com.son.soccerStreaming.auth.dto.AuthResponseDto;
import com.son.soccerStreaming.auth.entity.AppUser;
import com.son.soccerStreaming.global.exception.CustomException;
import com.son.soccerStreaming.global.exception.ErrorCode;
import com.son.soccerStreaming.auth.repository.AppUserRepository;
import com.son.soccerStreaming.auth.security.AuthUserDetails;
import com.son.soccerStreaming.favorite.repository.FavoritePlayerRepository;
import com.son.soccerStreaming.favorite.repository.FavoriteTeamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private FavoriteTeamRepository favoriteTeamRepository;

    @Mock
    private FavoritePlayerRepository favoritePlayerRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    @Test
    void signupCreatesUserAndStoresSecurityContextInSession() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        AuthRequestDto.Signup request = new AuthRequestDto.Signup("USER@example.com", "password", "Fan");
        AppUser savedUser = AppUser.builder()
                .email("user@example.com")
                .password("encoded-password")
                .nickname("Fan")
                .build();

        when(appUserRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password")).thenReturn("encoded-password");
        when(appUserRepository.saveAndFlush(any())).thenReturn(savedUser);
        when(authenticationManager.authenticate(any()))
                .thenReturn(new UsernamePasswordAuthenticationToken(new AuthUserDetails(savedUser), null));

        AuthResponseDto.Me response = authService.signup(request, servletRequest);

        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).saveAndFlush(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("user@example.com");
        assertThat(userCaptor.getValue().getPassword()).isEqualTo("encoded-password");
        assertThat(response.getEmail()).isEqualTo("user@example.com");
        assertThat(servletRequest.getSession(false)
                .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))
                .isNotNull();
    }

    @Test
    void signupRejectsDuplicateEmail() {
        when(appUserRepository.existsByEmail("fan@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(
                new AuthRequestDto.Signup("fan@example.com", "password", "Fan"),
                new MockHttpServletRequest()
        ))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
    }

    @Test
    void signupRejectsDuplicateEmailRaceCondition() {
        when(appUserRepository.existsByEmail("fan@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password")).thenReturn("encoded-password");
        when(appUserRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> authService.signup(
                new AuthRequestDto.Signup("fan@example.com", "password", "Fan"),
                new MockHttpServletRequest()
        ))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
    }

    @Test
    void signupRejectsInvalidEmailFormat() {
        assertThatThrownBy(() -> authService.signup(
                new AuthRequestDto.Signup("not-an-email", "password", "Fan"),
                new MockHttpServletRequest()
        ))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_AUTH_REQUEST);
    }

    @Test
    void signupRejectsTooLongEmail() {
        String longEmail = "a".repeat(121) + "@example.com";

        assertThatThrownBy(() -> authService.signup(
                new AuthRequestDto.Signup(longEmail, "password", "Fan"),
                new MockHttpServletRequest()
        ))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_AUTH_REQUEST);
    }

    @Test
    void signupRejectsShortPassword() {
        assertThatThrownBy(() -> authService.signup(
                new AuthRequestDto.Signup("fan@example.com", "short", "Fan"),
                new MockHttpServletRequest()
        ))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_AUTH_REQUEST);
    }

    @Test
    void signupRejectsTooLongPassword() {
        assertThatThrownBy(() -> authService.signup(
                new AuthRequestDto.Signup("fan@example.com", "a".repeat(21), "Fan"),
                new MockHttpServletRequest()
        ))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_AUTH_REQUEST);
    }

    @Test
    void signupRejectsInvalidNickname() {
        assertThatThrownBy(() -> authService.signup(
                new AuthRequestDto.Signup("fan@example.com", "password", "Fan!"),
                new MockHttpServletRequest()
        ))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_AUTH_REQUEST);
    }

    @Test
    void signupRejectsTooLongNickname() {
        assertThatThrownBy(() -> authService.signup(
                new AuthRequestDto.Signup("fan@example.com", "password", "a".repeat(21)),
                new MockHttpServletRequest()
        ))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_AUTH_REQUEST);
    }

    @Test
    void loginReturnsCurrentUser() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        AppUser user = AppUser.builder()
                .email("fan@example.com")
                .password("encoded-password")
                .nickname("Fan")
                .build();
        Authentication authentication = new UsernamePasswordAuthenticationToken(new AuthUserDetails(user), null);

        when(authenticationManager.authenticate(any())).thenReturn(authentication);

        AuthResponseDto.Me response = authService.login(
                new AuthRequestDto.Login("fan@example.com", "password"),
                servletRequest
        );

        assertThat(response.getEmail()).isEqualTo("fan@example.com");
        assertThat(response.getNickname()).isEqualTo("Fan");
    }

    @Test
    void loginRejectsWrongPassword() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> authService.login(
                new AuthRequestDto.Login("fan@example.com", "wrong"),
                new MockHttpServletRequest()
        ))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LOGIN_FAILED);
    }

    @Test
    void loginRejectsInvalidEmailFormat() {
        assertThatThrownBy(() -> authService.login(
                new AuthRequestDto.Login("not-an-email", "password"),
                new MockHttpServletRequest()
        ))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_AUTH_REQUEST);
    }

    @Test
    void meRequiresAuthentication() {
        assertThatThrownBy(() -> authService.me(null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED);
    }

    @Test
    void updateNicknameReturnsUpdatedCurrentUserAndStoresSecurityContext() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        AppUser user = AppUser.builder()
                .id(1L)
                .email("fan@example.com")
                .password("encoded-password")
                .nickname("Old")
                .build();

        when(appUserRepository.findById(1L)).thenReturn(Optional.of(user));

        AuthResponseDto.Me response = authService.updateNickname(
                new AuthUserDetails(user),
                new AuthRequestDto.UpdateNickname("New Fan"),
                servletRequest
        );

        assertThat(response.getNickname()).isEqualTo("New Fan");
        assertThat(servletRequest.getSession(false)
                .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))
                .isNotNull();
    }

    @Test
    void changePasswordRequiresCurrentPassword() {
        AppUser user = AppUser.builder()
                .id(1L)
                .email("fan@example.com")
                .password("encoded-password")
                .nickname("Fan")
                .build();

        when(appUserRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(
                new AuthUserDetails(user),
                new AuthRequestDto.ChangePassword("wrong-password", "newpass12")
        ))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CURRENT_PASSWORD);

        verify(passwordEncoder, never()).encode("newpass12");
    }

    @Test
    void changePasswordUpdatesEncodedPassword() {
        AppUser user = AppUser.builder()
                .id(1L)
                .email("fan@example.com")
                .password("encoded-password")
                .nickname("Fan")
                .build();

        when(appUserRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "encoded-password")).thenReturn(true);
        when(passwordEncoder.encode("newpass12")).thenReturn("new-encoded-password");

        authService.changePassword(
                new AuthUserDetails(user),
                new AuthRequestDto.ChangePassword("password", "newpass12")
        );

        assertThat(user.getPassword()).isEqualTo("new-encoded-password");
    }

    @Test
    void deleteAccountRemovesFavoritesBeforeUser() {
        AppUser user = AppUser.builder()
                .id(1L)
                .email("fan@example.com")
                .password("encoded-password")
                .nickname("Fan")
                .build();

        when(appUserRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "encoded-password")).thenReturn(true);

        authService.deleteAccount(new AuthUserDetails(user), new AuthRequestDto.DeleteAccount("password"));

        verify(favoritePlayerRepository).deleteByUserId(1L);
        verify(favoriteTeamRepository).deleteByUserId(1L);
        verify(appUserRepository).delete(user);
    }
}
