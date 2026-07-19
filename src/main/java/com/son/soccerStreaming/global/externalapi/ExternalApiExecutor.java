package com.son.soccerStreaming.global.externalapi;

import com.son.soccerStreaming.apifootball.client.ApiFootballCircuitOpenException;
import com.son.soccerStreaming.apifootball.service.ApiFootballSyncStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalApiExecutor {
    private final ApiFootballSyncStatusService syncStatusService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${external-api.retry.max-attempts:3}") private int maxAttempts;
    @Value("${external-api.retry.initial-delay:500ms}") private Duration initialDelay;
    @Value("${external-api.retry.max-delay:5s}") private Duration maxDelay;
    @Value("${external-api.retry.max-retry-after:30s}") private Duration maxRetryAfter;
    @Value("${external-api.retry.jitter-max:250ms}") private Duration jitterMax;

    public <T> T execute(ExternalApiProvider provider, String operation, Supplier<T> request) {
        return execute(provider, operation, ExternalApiInvocationContext.system(), request);
    }

    public <T> T execute(ExternalApiProvider provider, String operation,
                         ExternalApiInvocationContext context, Supplier<T> request) {
        long startedAt = System.nanoTime();
        int configuredAttempts = Math.max(1, maxAttempts);
        for (int attempt = 1; attempt <= configuredAttempts; attempt++) {
            try {
                T result = request.get();
                recordSuccess(provider, operation, attempt);
                publish(provider, operation, context, true, attempt, startedAt, result, null);
                return result;
            } catch (Exception exception) {
                ExternalApiException failure = classify(provider, operation, exception);
                if (!failure.isRetryable() || attempt >= configuredAttempts) {
                    recordFailure(provider, operation, attempt, failure);
                    publish(provider, operation, context, false, attempt, startedAt, null, failure);
                    throw failure;
                }
                Duration delay = retryDelay(failure, attempt);
                log.warn("External API request will retry. provider={}, operation={}, category={}, status={}, attempt={}/{}, retryDelayMs={}",
                        provider, operation, failure.getCategory(), failure.getHttpStatus(),
                        attempt, configuredAttempts, delay.toMillis());
                try {
                    sleep(delay, provider, operation);
                } catch (ExternalApiException interrupted) {
                    recordFailure(provider, operation, attempt, interrupted);
                    publish(provider, operation, context, false, attempt, startedAt, null, interrupted);
                    throw interrupted;
                }
            }
        }
        throw new IllegalStateException("External API retry loop ended unexpectedly");
    }

    ExternalApiException classify(ExternalApiProvider provider, String operation, Exception exception) {
        if (exception instanceof ExternalApiException externalApiException) return externalApiException;
        if (exception instanceof ApiFootballCircuitOpenException) {
            return failure(provider, operation, ExternalApiErrorCategory.CIRCUIT_OPEN, null, false, null,
                    "External API circuit is open", exception);
        }
        if (exception instanceof RestClientResponseException responseException) {
            int status = responseException.getStatusCode().value();
            String body = responseException.getResponseBodyAsString().toLowerCase(Locale.ROOT);
            Duration retryAfter = retryAfter(responseException.getResponseHeaders());
            if (status == 429) {
                boolean quota = isQuotaError(body);
                return failure(provider, operation,
                        quota ? ExternalApiErrorCategory.QUOTA_EXHAUSTED : ExternalApiErrorCategory.RATE_LIMITED,
                        status, !quota, retryAfter,
                        quota ? "External API quota is exhausted" : "External API rate limit was reached", exception);
            }
            if (status == 408 || (provider == ExternalApiProvider.API_FOOTBALL && status == 499)) {
                return failure(provider, operation, ExternalApiErrorCategory.TIMEOUT, status, true, retryAfter,
                        "External API request timed out", exception);
            }
            if (status == 400) return failure(provider, operation, ExternalApiErrorCategory.BAD_REQUEST, status, false, null, "External API rejected the request", exception);
            if (status == 401) return failure(provider, operation, ExternalApiErrorCategory.AUTHENTICATION, status, false, null, "External API authentication failed", exception);
            if (status == 403) return failure(provider, operation, ExternalApiErrorCategory.PERMISSION, status, false, null, "External API permission was denied", exception);
            if (status == 404) return failure(provider, operation, ExternalApiErrorCategory.NOT_FOUND, status, false, null, "External API resource was not found", exception);
            if (status == 500 || status == 502 || status == 503 || status == 504) {
                return failure(provider, operation, ExternalApiErrorCategory.UPSTREAM_SERVER, status, true, retryAfter, "External API server failed", exception);
            }
            return failure(provider, operation, ExternalApiErrorCategory.BAD_REQUEST, status, false, null, "External API returned an unsuccessful response", exception);
        }
        if (hasCause(exception, ConnectException.class) || hasCause(exception, HttpConnectTimeoutException.class)) {
            return failure(provider, operation, ExternalApiErrorCategory.NETWORK, null, true, null, "External API connection failed", exception);
        }
        if (hasCause(exception, HttpTimeoutException.class) || hasCause(exception, SocketTimeoutException.class)) {
            return failure(provider, operation, ExternalApiErrorCategory.TIMEOUT, null, true, null, "External API request timed out", exception);
        }
        if (hasCause(exception, HttpMessageConversionException.class)) {
            return failure(provider, operation, ExternalApiErrorCategory.INVALID_RESPONSE, null, false, null,
                    "External API response was invalid", exception);
        }
        if (exception instanceof RestClientException) {
            return failure(provider, operation, ExternalApiErrorCategory.NETWORK, null, true, null, "External API network request failed", exception);
        }
        return failure(provider, operation, ExternalApiErrorCategory.UNKNOWN, null, false, null, "External API call failed", exception);
    }

    private ExternalApiException failure(ExternalApiProvider provider, String operation,
                                         ExternalApiErrorCategory category, Integer status,
                                         boolean retryable, Duration retryAfter, String message, Exception cause) {
        return new ExternalApiException(provider, operation, category, status, retryable, retryAfter, message, cause);
    }

    private boolean isQuotaError(String body) {
        return body.contains("insufficient_quota") || body.contains("run out of searches")
                || body.contains("quota exhausted") || body.contains("billing")
                || body.contains("credits exhausted") || body.contains("no credits");
    }

    private Duration retryAfter(HttpHeaders headers) {
        if (headers == null) return null;
        String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null || value.isBlank()) return null;
        try {
            return Duration.ofSeconds(Math.max(0, Long.parseLong(value.trim())));
        } catch (NumberFormatException ignored) {
            try {
                ZonedDateTime retryAt = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME);
                Duration delay = Duration.between(ZonedDateTime.now(retryAt.getZone()), retryAt);
                return delay.isNegative() ? Duration.ZERO : delay;
            } catch (DateTimeParseException ignoredDate) {
                return null;
            }
        }
    }

    private Duration retryDelay(ExternalApiException failure, int attempt) {
        if (failure.getRetryAfter() != null) return min(failure.getRetryAfter(), maxRetryAfter);
        long exponential = Math.min(maxDelay.toMillis(), initialDelay.toMillis() * (1L << Math.min(attempt - 1, 20)));
        long jitterBound = Math.max(0, jitterMax.toMillis());
        long jitter = jitterBound == 0 ? 0 : ThreadLocalRandom.current().nextLong(jitterBound + 1);
        return Duration.ofMillis(Math.min(maxDelay.toMillis(), exponential + jitter));
    }

    private Duration min(Duration left, Duration right) { return left.compareTo(right) <= 0 ? left : right; }

    private void sleep(Duration delay, ExternalApiProvider provider, String operation) {
        try {
            Thread.sleep(Math.max(0, delay.toMillis()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure(provider, operation, ExternalApiErrorCategory.NETWORK, null, false, null,
                    "External API retry was interrupted", exception);
        }
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (type.isInstance(current)) return true;
        }
        return false;
    }

    private void recordSuccess(ExternalApiProvider provider, String operation, int attempts) {
        try {
            syncStatusService.recordProviderSuccess(provider, operation, attempts);
        } catch (RuntimeException statusFailure) {
            log.error("Failed to persist external API success status. provider={}, operation={}",
                    provider, operation, statusFailure);
        }
    }

    private void recordFailure(ExternalApiProvider provider, String operation, int attempts,
                               ExternalApiException failure) {
        try {
            syncStatusService.recordProviderFailure(provider, operation, attempts, failure);
        } catch (RuntimeException statusFailure) {
            log.error("Failed to persist external API failure status. provider={}, operation={}",
                    provider, operation, statusFailure);
        }
    }

    private void publish(ExternalApiProvider provider, String operation, ExternalApiInvocationContext context,
                         boolean success, int attempts, long startedAt, Object result, ExternalApiException failure) {
        try {
            eventPublisher.publishEvent(new ExternalApiCallCompletedEvent(provider, operation, context, success,
                    attempts, (System.nanoTime() - startedAt) / 1_000_000,
                    result instanceof Collection<?> collection ? collection.size() : null,
                    failure == null ? null : failure.getHttpStatus(),
                    failure == null ? null : failure.getCategory()));
        } catch (RuntimeException eventFailure) {
            log.error("Failed to publish external API completion event. provider={}, operation={}", provider, operation, eventFailure);
        }
    }
}
