package com.son.soccerStreaming.global.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // 400 BAD_REQUEST
    INVALID_AUTH_REQUEST(HttpStatus.BAD_REQUEST, "이메일, 비밀번호, 닉네임을 확인해 주세요. 비밀번호는 8자 이상이어야 합니다."),
    INVALID_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다."),
    INVALID_ADMIN_OVERRIDE_FIELD(HttpStatus.BAD_REQUEST, "Invalid admin override field."),
    INVALID_ADMIN_EVENT_FIELD(HttpStatus.BAD_REQUEST, "Invalid admin event field."),
    INVALID_ADMIN_SEARCH_KEYWORD(HttpStatus.BAD_REQUEST, "Admin search keyword is too long."),
    INVALID_ADMIN_SYNC_COVERAGE(HttpStatus.BAD_REQUEST, "This season does not support the requested API-Football data."),
    INVALID_ADMIN_MEDIA_REQUEST(HttpStatus.BAD_REQUEST, "이미지는 PNG, JPEG, WebP 형식의 2MB 이하 파일만 업로드할 수 있습니다."),
    INVALID_ADMIN_MEDIA_OBJECT(HttpStatus.BAD_REQUEST, "업로드된 이미지가 요청한 대상 또는 파일 조건과 일치하지 않습니다."),
    ADMIN_SYNC_TOO_FREQUENT(HttpStatus.TOO_MANY_REQUESTS, "Manual sync was requested too frequently. Please wait before retrying."),

    // 401 UNAUTHORIZED
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),

    // 404 NOT_FOUND
    PLAYER_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 선수를 찾을 수 없습니다."),
    TEAM_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 팀을 찾을 수 없습니다."),
    VENUE_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 경기장을 찾을 수 없습니다."),
    FIXTURE_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 경기를 찾을 수 없습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    ADMIN_MEDIA_OBJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "업로드된 이미지 객체를 찾을 수 없습니다."),

    // 503 SERVICE_UNAVAILABLE
    ADMIN_MEDIA_STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "이미지 저장소를 사용할 수 없습니다."),

    // 500 INTERNAL_SERVER_ERROR
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "예기치 못한 서버 오류가 발생했습니다."),

    // 409 CONFLICT
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입된 이메일입니다.");

    private final HttpStatus status;
    private final String message;
}
