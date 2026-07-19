package com.son.soccerStreaming.apifootball.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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
}
