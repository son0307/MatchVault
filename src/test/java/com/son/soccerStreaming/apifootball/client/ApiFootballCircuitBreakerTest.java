package com.son.soccerStreaming.apifootball.client;

import com.son.soccerStreaming.apifootball.service.ApiFootballSyncStatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ApiFootballCircuitBreakerTest {

    private ApiFootballSyncStatusService syncStatusService;
    private ApiFootballCircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        syncStatusService = mock(ApiFootballSyncStatusService.class);
        circuitBreaker = new ApiFootballCircuitBreaker(syncStatusService);
        ReflectionTestUtils.setField(circuitBreaker, "enabled", true);
        ReflectionTestUtils.setField(circuitBreaker, "failureThreshold", 2);
        ReflectionTestUtils.setField(circuitBreaker, "cooldownMs", 60_000L);
    }

    @Test
    void recordsGlobalFailureWhenCircuitOpensAndRecoveryOnFirstSuccess() {
        RuntimeException failure = new RuntimeException("API unavailable");
        circuitBreaker.recordSuccess();

        circuitBreaker.recordFailure("fixtures", failure);
        verify(syncStatusService, never()).recordFailureByKey("api-football", "API-Football", failure);

        circuitBreaker.recordFailure("standings", failure);
        verify(syncStatusService).recordFailureByKey("api-football", "API-Football", failure);
        assertThatThrownBy(() -> circuitBreaker.beforeRequest("players"))
                .isInstanceOf(ApiFootballCircuitOpenException.class);

        circuitBreaker.recordSuccess();
        circuitBreaker.recordSuccess();

        verify(syncStatusService, times(3)).recordSuccessByKey("api-football", "API-Football");
        circuitBreaker.beforeRequest("players");
    }

    @Test
    void recordsEverySuccessfulApiResponseAsGlobalHealthActivity() {
        circuitBreaker.recordSuccess();
        circuitBreaker.recordSuccess();

        verify(syncStatusService, times(2)).recordSuccessByKey("api-football", "API-Football");
    }
}
