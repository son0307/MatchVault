package com.son.soccerStreaming.global.externalapi;

import lombok.Getter;
import java.time.Duration;

@Getter
public class ExternalApiException extends RuntimeException {
    private final ExternalApiProvider provider;
    private final String operation;
    private final ExternalApiErrorCategory category;
    private final Integer httpStatus;
    private final boolean retryable;
    private final Duration retryAfter;

    public ExternalApiException(ExternalApiProvider provider, String operation,
                                ExternalApiErrorCategory category, Integer httpStatus,
                                boolean retryable, Duration retryAfter, String message, Throwable cause) {
        super(message, cause);
        this.provider = provider;
        this.operation = operation;
        this.category = category;
        this.httpStatus = httpStatus;
        this.retryable = retryable;
        this.retryAfter = retryAfter;
    }
}
