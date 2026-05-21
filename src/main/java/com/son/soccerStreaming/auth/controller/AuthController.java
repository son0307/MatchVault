package com.son.soccerStreaming.auth.controller;

import com.son.soccerStreaming.auth.dto.AuthRequestDto;
import com.son.soccerStreaming.auth.dto.AuthResponseDto;
import com.son.soccerStreaming.auth.security.AuthUserDetails;
import com.son.soccerStreaming.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "회원가입과 세션 로그인 API")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "회원가입", description = "이메일 계정을 만들고 세션에 로그인합니다.")
    @PostMapping("/signup")
    public ResponseEntity<AuthResponseDto.Me> signup(
            @RequestBody AuthRequestDto.Signup request,
            HttpServletRequest servletRequest
    ) {
        return ResponseEntity.ok(authService.signup(request, servletRequest));
    }

    @Operation(summary = "로그인", description = "이메일과 비밀번호로 세션을 생성합니다.")
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto.Me> login(
            @RequestBody AuthRequestDto.Login request,
            HttpServletRequest servletRequest
    ) {
        return ResponseEntity.ok(authService.login(request, servletRequest));
    }

    @Operation(summary = "로그아웃", description = "현재 세션을 종료합니다.")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "내 정보", description = "현재 로그인한 사용자 정보를 조회합니다.")
    @GetMapping("/me")
    public ResponseEntity<AuthResponseDto.Me> me(@AuthenticationPrincipal AuthUserDetails userDetails) {
        return ResponseEntity.ok(authService.me(userDetails));
    }
}
