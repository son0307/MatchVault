package com.son.soccerStreaming.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // 404 NOT_FOUND
    PLAYER_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 선수를 찾을 수 없습니다."),
    TEAM_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 팀을 찾을 수 없습니다."),
    MATCH_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 경기를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
