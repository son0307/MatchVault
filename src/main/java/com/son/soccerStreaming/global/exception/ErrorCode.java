package com.son.soccerStreaming.global.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // 400 BAD_REQUEST
    INVALID_AUTH_REQUEST(HttpStatus.BAD_REQUEST, "이메일, 비밀번호, 닉네임을 확인해 주세요. 비밀번호는 8자 이상이어야 합니다."),
    INVALID_ADMIN_OVERRIDE_FIELD(HttpStatus.BAD_REQUEST, "Invalid admin override field."),

    // 401 UNAUTHORIZED
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),

    // 404 NOT_FOUND
    PLAYER_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 선수를 찾을 수 없습니다."),
    TEAM_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 팀을 찾을 수 없습니다."),
    FIXTURE_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 경기를 찾을 수 없습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),

    // 409 CONFLICT
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입된 이메일입니다.");

    private final HttpStatus status;
    private final String message;
}
