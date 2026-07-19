package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.apifootball.entity.ApiFootballSyncStatus;
import com.son.soccerStreaming.apifootball.repository.ApiFootballSyncStatusRepository;
import com.son.soccerStreaming.global.externalapi.ExternalApiErrorCategory;
import com.son.soccerStreaming.global.externalapi.ExternalApiException;
import com.son.soccerStreaming.global.externalapi.ExternalApiProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiFootballSyncStatusServiceTest {
    private ApiFootballSyncStatusRepository repository;
    private ApiFootballSyncStatusService service;

    @BeforeEach
    void setUp() {
        repository = mock(ApiFootballSyncStatusRepository.class);
        service = new ApiFootballSyncStatusService(repository);
    }

    @Test
    void createsProviderSnapshotForFinalFailure() {
        when(repository.findById("openai")).thenReturn(Optional.empty());
        ExternalApiException failure = new ExternalApiException(
                ExternalApiProvider.OPENAI,
                "translate-news-titles",
                ExternalApiErrorCategory.RATE_LIMITED,
                429,
                true,
                Duration.ofSeconds(2),
                "External API rate limit was reached",
                null
        );

        service.recordProviderFailure(ExternalApiProvider.OPENAI, "translate-news-titles", 3, failure);

        ArgumentCaptor<ApiFootballSyncStatus> captor = ArgumentCaptor.forClass(ApiFootballSyncStatus.class);
        verify(repository).save(captor.capture());
        ApiFootballSyncStatus status = captor.getValue();
        assertThat(status.getSyncKey()).isEqualTo("openai");
        assertThat(status.getProvider()).isEqualTo("OPENAI");
        assertThat(status.getStatus()).isEqualTo("FAILED");
        assertThat(status.getFailureCount()).isEqualTo(1);
        assertThat(status.getLastOperation()).isEqualTo("translate-news-titles");
        assertThat(status.getLastErrorCategory()).isEqualTo("RATE_LIMITED");
        assertThat(status.getLastHttpStatus()).isEqualTo(429);
        assertThat(status.getLastAttemptCount()).isEqualTo(3);
    }

    @Test
    void successRecoversExistingProviderSnapshotAndKeepsLastFailureTime() {
        ApiFootballSyncStatus status = ApiFootballSyncStatus.builder()
                .syncKey("serp-api")
                .displayName("SerpAPI")
                .lastSyncedAt(java.time.LocalDateTime.now())
                .failureCount(0)
                .build();
        status.recordProviderFailure("SerpAPI", "SERP_API", "search-team-news", "UPSTREAM_SERVER",
                503, 3, java.time.LocalDateTime.now(), "External API server failed");
        when(repository.findById("serp-api")).thenReturn(Optional.of(status));

        service.recordProviderSuccess(ExternalApiProvider.SERP_API, "search-team-news", 2);

        assertThat(status.getStatus()).isEqualTo("OK");
        assertThat(status.getFailureCount()).isZero();
        assertThat(status.getLastErrorMessage()).isNull();
        assertThat(status.getLastErrorCategory()).isNull();
        assertThat(status.getLastHttpStatus()).isNull();
        assertThat(status.getLastAttemptCount()).isEqualTo(2);
        assertThat(status.getLastFailureAt()).isNotNull();
        assertThat(status.getLastSuccessAt()).isNotNull();
        verify(repository).save(status);
    }

    @Test
    void fillsProviderWhenLegacyTaskRowIsUpdated() {
        ApiFootballSyncStatus legacy = ApiFootballSyncStatus.builder()
                .syncKey("teams:2025")
                .displayName("Teams 2025")
                .lastSyncedAt(java.time.LocalDateTime.now())
                .failureCount(0)
                .build();
        when(repository.findById("teams:2025")).thenReturn(Optional.of(legacy));

        service.recordSuccess("teams", "Teams", 2025);

        assertThat(legacy.getProvider()).isEqualTo("API_FOOTBALL");
        verify(repository).save(legacy);
    }
}
