package com.son.soccerStreaming.exception;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice   // 프로젝트 전역에서 발생하는 예외 처리
public class GlobalExceptionHandler {

    // CustomException 발생 시 처리하는 메서드
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustomException(CustomException e) {
        log.error("CustomException 발생: {}", e.getErrorCode().getMessage());

        return ResponseEntity
                .status(e.getErrorCode().getStatus())
                .body(ErrorResponse.builder()
                        .status(e.getErrorCode().getStatus().value())
                        .error(e.getErrorCode().name())
                        .message(e.getErrorCode().getMessage())
                        .build()
                );
    }

    // 클라이언트에게 전송할 에러 포맷
    @Getter
    @Builder
    public static class ErrorResponse {
        private final int status;
        private final String error;
        private final String message;
    }
}
