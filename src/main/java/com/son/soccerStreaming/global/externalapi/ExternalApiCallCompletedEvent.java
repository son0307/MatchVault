package com.son.soccerStreaming.global.externalapi;

public record ExternalApiCallCompletedEvent(
        ExternalApiProvider provider, String operation, ExternalApiInvocationContext context,
        boolean success, int attempts, long durationMs, Integer resultCount, Integer httpStatus,
        ExternalApiErrorCategory errorCategory) {
}
