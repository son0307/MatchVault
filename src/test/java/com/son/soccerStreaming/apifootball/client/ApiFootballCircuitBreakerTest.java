package com.son.soccerStreaming.apifootball.client;

import com.son.soccerStreaming.global.externalapi.ExternalApiErrorCategory;
import com.son.soccerStreaming.global.externalapi.ExternalApiException;
import com.son.soccerStreaming.global.externalapi.ExternalApiProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiFootballCircuitBreakerTest {

    private ApiFootballCircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        circuitBreaker = new ApiFootballCircuitBreaker();
        ReflectionTestUtils.setField(circuitBreaker, "enabled", true);
        ReflectionTestUtils.setField(circuitBreaker, "failureThreshold", 2);
        ReflectionTestUtils.setField(circuitBreaker, "cooldownMs", 60_000L);
    }

    @Test
    void opensAfterThresholdAndRecoversOnSuccess() {
        RuntimeException failure = new RuntimeException("API unavailable");
        circuitBreaker.recordSuccess();

        circuitBreaker.recordFailure("fixtures", failure);
        circuitBreaker.recordFailure("standings", failure);
        assertThatThrownBy(() -> circuitBreaker.beforeRequest("players"))
                .isInstanceOf(ApiFootballCircuitOpenException.class);

        circuitBreaker.recordSuccess();
        circuitBreaker.recordSuccess();

        circuitBreaker.beforeRequest("players");
    }

    @Test
    void ignoresRequestAndRateLimitFailures() {
        circuitBreaker.recordFailure("fixtures", failure(ExternalApiErrorCategory.BAD_REQUEST));
        circuitBreaker.recordFailure("fixtures", failure(ExternalApiErrorCategory.RATE_LIMITED));

        circuitBreaker.beforeRequest("players");
    }

    @Test
    void opensForProviderAvailabilityFailures() {
        circuitBreaker.recordFailure("fixtures", failure(ExternalApiErrorCategory.TIMEOUT));
        circuitBreaker.recordFailure("standings", failure(ExternalApiErrorCategory.UPSTREAM_SERVER));

        assertThatThrownBy(() -> circuitBreaker.beforeRequest("players"))
                .isInstanceOf(ApiFootballCircuitOpenException.class);
    }

    private ExternalApiException failure(ExternalApiErrorCategory category) {
        return new ExternalApiException(
                ExternalApiProvider.API_FOOTBALL,
                "test",
                category,
                null,
                false,
                Duration.ZERO,
                "test failure",
                null
        );
    }
}
