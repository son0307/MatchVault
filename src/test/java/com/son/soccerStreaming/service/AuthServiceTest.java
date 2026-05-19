package com.son.soccerStreaming.service;

import com.son.soccerStreaming.dto.AuthRequestDto;
import com.son.soccerStreaming.dto.AuthResponseDto;
import com.son.soccerStreaming.entity.AppUser;
import com.son.soccerStreaming.exception.CustomException;
import com.son.soccerStreaming.exception.ErrorCode;
import com.son.soccerStreaming.repository.AppUserRepository;
import com.son.soccerStreaming.security.AuthUserDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

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
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new AuthUserDetails(savedUser),
                null
        );

        when(appUserRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password")).thenReturn("encoded-password");
        when(authenticationManager.authenticate(any())).thenReturn(authentication);

        AuthResponseDto.Me response = authService.signup(request, servletRequest);

        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(userCaptor.capture());
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
    void meRequiresAuthentication() {
        assertThatThrownBy(() -> authService.me(null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED);
    }
}
