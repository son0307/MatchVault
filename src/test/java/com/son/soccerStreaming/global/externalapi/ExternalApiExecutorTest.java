package com.son.soccerStreaming.global.externalapi;

import tools.jackson.databind.ObjectMapper;
import com.son.soccerStreaming.apifootball.service.ApiFootballSyncStatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ExternalApiExecutorTest {
    private ApiFootballSyncStatusService statusService;
    private ApplicationEventPublisher eventPublisher;
    private ExternalApiExecutor executor;

    @BeforeEach
    void setUp() {
        statusService = mock(ApiFootballSyncStatusService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        executor = new ExternalApiExecutor(statusService, eventPublisher, new ObjectMapper());
        ReflectionTestUtils.setField(executor, "maxAttempts", 3);
        ReflectionTestUtils.setField(executor, "initialDelay", Duration.ZERO);
        ReflectionTestUtils.setField(executor, "maxDelay", Duration.ZERO);
        ReflectionTestUtils.setField(executor, "maxRetryAfter", Duration.ZERO);
        ReflectionTestUtils.setField(executor, "jitterMax", Duration.ZERO);
    }

    @Test
    void retriesTransientServerErrorsAndRecordsRecovery() {
        AtomicInteger calls = new AtomicInteger();

        String result = executor.execute(ExternalApiProvider.OPENAI, "translate", () -> {
            if (calls.incrementAndGet() < 3) {
                throw HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "unavailable",
                        HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(3);
        verify(statusService).recordProviderSuccess(ExternalApiProvider.OPENAI, "translate", 3);
        verify(statusService, never()).recordProviderFailure(any(), any(), eq(3), any());
    }

    @Test
    void doesNotRetryAuthenticationFailures() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> executor.execute(ExternalApiProvider.SERP_API, "search", () -> {
            calls.incrementAndGet();
            throw HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "unauthorized",
                    HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);
        })).isInstanceOfSatisfying(ExternalApiException.class, failure -> {
            assertThat(failure.getCategory()).isEqualTo(ExternalApiErrorCategory.AUTHENTICATION);
            assertThat(failure.isRetryable()).isFalse();
        });

        assertThat(calls).hasValue(1);
        verify(statusService).recordProviderFailure(eq(ExternalApiProvider.SERP_API), eq("search"), eq(1), any());
    }

    @Test
    void distinguishesQuotaExhaustionFromRateLimit() {
        var quota = HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "limited", HttpHeaders.EMPTY,
                "{\"error\":{\"code\":\"insufficient_quota\",\"type\":\"insufficient_quota\",\"message\":\"You exceeded your current quota\"}}"
                        .getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        var rate = HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "limited", HttpHeaders.EMPTY,
                "{\"error\":{\"code\":\"rate_limit_exceeded\",\"type\":\"requests\",\"message\":\"Rate limit reached\"}}"
                        .getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);

        ExternalApiException quotaFailure = executor.classify(ExternalApiProvider.OPENAI, "translate", quota);
        ExternalApiException rateFailure = executor.classify(ExternalApiProvider.OPENAI, "translate", rate);

        assertThat(quotaFailure.getCategory()).isEqualTo(ExternalApiErrorCategory.QUOTA_EXHAUSTED);
        assertThat(quotaFailure.isRetryable()).isFalse();
        assertThat(rateFailure.getCategory()).isEqualTo(ExternalApiErrorCategory.RATE_LIMITED);
        assertThat(rateFailure.isRetryable()).isTrue();
    }

    @Test
    void classifiesSerpApiQuotaUsingItsStructuredErrorField() {
        var quota = HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "limited", HttpHeaders.EMPTY,
                "{\"error\":\"Your account has run out of searches.\"}"
                        .getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        var throughput = HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "limited", HttpHeaders.EMPTY,
                "{\"error\":\"Hourly throughput limit exceeded.\"}"
                        .getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);

        ExternalApiException quotaFailure = executor.classify(ExternalApiProvider.SERP_API, "search", quota);
        ExternalApiException throughputFailure = executor.classify(ExternalApiProvider.SERP_API, "search", throughput);

        assertThat(quotaFailure.getCategory()).isEqualTo(ExternalApiErrorCategory.QUOTA_EXHAUSTED);
        assertThat(quotaFailure.isRetryable()).isFalse();
        assertThat(throughputFailure.getCategory()).isEqualTo(ExternalApiErrorCategory.RATE_LIMITED);
        assertThat(throughputFailure.isRetryable()).isTrue();
    }

    @Test
    void keepsUnknownOrMalformed429RetryableWithoutGuessingQuota() {
        var malformed = HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "limited", HttpHeaders.EMPTY,
                "not-json".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);

        ExternalApiException failure = executor.classify(ExternalApiProvider.OPENAI, "translate", malformed);

        assertThat(failure.getCategory()).isEqualTo(ExternalApiErrorCategory.RATE_LIMITED);
        assertThat(failure.isRetryable()).isTrue();
    }

    @Test
    void classifiesPermanentHttpErrorsWithoutRetry() {
        assertCategory(400, ExternalApiErrorCategory.BAD_REQUEST, false);
        assertCategory(401, ExternalApiErrorCategory.AUTHENTICATION, false);
        assertCategory(403, ExternalApiErrorCategory.PERMISSION, false);
        assertCategory(404, ExternalApiErrorCategory.NOT_FOUND, false);
    }

    @Test
    void classifiesOnlyConfiguredTransientHttpErrorsAsRetryable() {
        assertCategory(408, ExternalApiErrorCategory.TIMEOUT, true);
        assertCategory(500, ExternalApiErrorCategory.UPSTREAM_SERVER, true);
        assertCategory(502, ExternalApiErrorCategory.UPSTREAM_SERVER, true);
        assertCategory(503, ExternalApiErrorCategory.UPSTREAM_SERVER, true);
        assertCategory(504, ExternalApiErrorCategory.UPSTREAM_SERVER, true);

        var status499 = HttpClientErrorException.create(HttpStatusCode.valueOf(499), "timeout",
                HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);
        ExternalApiException apiFootballFailure = executor.classify(
                ExternalApiProvider.API_FOOTBALL, "fixtures", status499);
        ExternalApiException openAiFailure = executor.classify(ExternalApiProvider.OPENAI, "translate", status499);
        assertThat(apiFootballFailure.getCategory()).isEqualTo(ExternalApiErrorCategory.TIMEOUT);
        assertThat(apiFootballFailure.isRetryable()).isTrue();
        assertThat(openAiFailure.isRetryable()).isFalse();
    }

    @Test
    void distinguishesConnectionReadTimeoutAndInvalidResponse() {
        ExternalApiException connectFailure = executor.classify(ExternalApiProvider.SERP_API, "search",
                new RestClientException("connect", new HttpConnectTimeoutException("connect timeout")));
        ExternalApiException readFailure = executor.classify(ExternalApiProvider.OPENAI, "translate",
                new RestClientException("read", new HttpTimeoutException("read timeout")));
        ExternalApiException invalidResponse = executor.classify(ExternalApiProvider.OPENAI, "translate",
                new HttpMessageConversionException("invalid JSON"));

        assertThat(connectFailure.getCategory()).isEqualTo(ExternalApiErrorCategory.NETWORK);
        assertThat(connectFailure.isRetryable()).isTrue();
        assertThat(readFailure.getCategory()).isEqualTo(ExternalApiErrorCategory.TIMEOUT);
        assertThat(readFailure.isRetryable()).isTrue();
        assertThat(invalidResponse.getCategory()).isEqualTo(ExternalApiErrorCategory.INVALID_RESPONSE);
        assertThat(invalidResponse.isRetryable()).isFalse();
    }

    @Test
    void stopsRetryingWhenThreadIsInterrupted() {
        ReflectionTestUtils.setField(executor, "initialDelay", Duration.ofSeconds(1));
        ReflectionTestUtils.setField(executor, "maxDelay", Duration.ofSeconds(1));
        AtomicInteger calls = new AtomicInteger();
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> executor.execute(ExternalApiProvider.SERP_API, "search", () -> {
                calls.incrementAndGet();
                throw HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "unavailable",
                        HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);
            })).isInstanceOfSatisfying(ExternalApiException.class, failure -> {
                assertThat(failure.isRetryable()).isFalse();
                assertThat(failure.getCategory()).isEqualTo(ExternalApiErrorCategory.NETWORK);
            });
        } finally {
            Thread.interrupted();
        }
        assertThat(calls).hasValue(1);
    }

    private void assertCategory(int status, ExternalApiErrorCategory category, boolean retryable) {
        var exception = HttpClientErrorException.create(HttpStatusCode.valueOf(status), "error",
                HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);
        ExternalApiException failure = executor.classify(ExternalApiProvider.OPENAI, "operation", exception);
        assertThat(failure.getCategory()).isEqualTo(category);
        assertThat(failure.isRetryable()).isEqualTo(retryable);
    }
}
