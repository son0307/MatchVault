package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.apifootball.entity.ApiFootballSyncStatus;
import com.son.soccerStreaming.apifootball.repository.ApiFootballSyncStatusRepository;
import com.son.soccerStreaming.global.externalapi.ExternalApiException;
import com.son.soccerStreaming.global.externalapi.ExternalApiProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ApiFootballSyncStatusService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final ApiFootballSyncStatusRepository apiFootballSyncStatusRepository;

    @Transactional
    public void recordAttempt(String syncKey, String displayName) {
        recordAttempt(syncKey, displayName, null);
    }

    @Transactional
    public void recordAttempt(String syncKey, String displayName, Integer season) {
        LocalDateTime now = LocalDateTime.now(KOREA_ZONE);
        ApiFootballSyncStatus status = status(syncKey, displayName, season, now);
        status.recordAttempt(displayName(displayName, season), now);
        status.markProvider(ExternalApiProvider.API_FOOTBALL.name());
        apiFootballSyncStatusRepository.save(status);
    }

    @Transactional
    public void recordSuccess(String syncKey, String displayName) {
        recordSuccess(syncKey, displayName, null);
    }

    @Transactional
    public void recordSuccess(String syncKey, String displayName, Integer season) {
        LocalDateTime now = LocalDateTime.now(KOREA_ZONE);
        ApiFootballSyncStatus status = status(syncKey, displayName, season, now);
        status.recordSuccess(displayName(displayName, season), now);
        status.markProvider(ExternalApiProvider.API_FOOTBALL.name());
        apiFootballSyncStatusRepository.save(status);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccessByKey(String syncKey, String displayName) {
        LocalDateTime now = LocalDateTime.now(KOREA_ZONE);
        ApiFootballSyncStatus status = statusByKey(syncKey, displayName, now);
        status.recordSuccess(displayName, now);
        status.markProvider(ExternalApiProvider.API_FOOTBALL.name());
        apiFootballSyncStatusRepository.save(status);
    }

    @Transactional
    public void recordFailure(String syncKey, String displayName, Integer season, Exception exception) {
        LocalDateTime now = LocalDateTime.now(KOREA_ZONE);
        ApiFootballSyncStatus status = status(syncKey, displayName, season, now);
        status.recordFailure(displayName(displayName, season), now, errorMessage(exception));
        status.markProvider(ExternalApiProvider.API_FOOTBALL.name());
        apiFootballSyncStatusRepository.save(status);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailureByKey(String syncKey, String displayName, Exception exception) {
        LocalDateTime now = LocalDateTime.now(KOREA_ZONE);
        ApiFootballSyncStatus status = statusByKey(syncKey, displayName, now);
        status.recordFailure(displayName, now, errorMessage(exception));
        status.markProvider(ExternalApiProvider.API_FOOTBALL.name());
        apiFootballSyncStatusRepository.save(status);
    }

    @Transactional
    public void recordRetryPendingByKey(String syncKey, String displayName, String message) {
        LocalDateTime now = LocalDateTime.now(KOREA_ZONE);
        ApiFootballSyncStatus status = statusByKey(syncKey, displayName, now);
        status.recordRetryPending(displayName, now, safeMessage(message));
        status.markProvider(ExternalApiProvider.API_FOOTBALL.name());
        apiFootballSyncStatusRepository.save(status);
    }

    @Transactional(readOnly = true)
    public Optional<ApiFootballSyncStatus> findByKey(String syncKey) {
        return apiFootballSyncStatusRepository.findById(syncKey);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordProviderSuccess(ExternalApiProvider provider, String operation, int attemptCount) {
        LocalDateTime now = LocalDateTime.now(KOREA_ZONE);
        ApiFootballSyncStatus status = statusByKey(provider.statusKey(), provider.displayName(), now);
        status.recordProviderSuccess(provider.displayName(), provider.name(), operation, attemptCount, now);
        apiFootballSyncStatusRepository.save(status);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordProviderFailure(ExternalApiProvider provider, String operation, int attemptCount,
                                      ExternalApiException exception) {
        LocalDateTime now = LocalDateTime.now(KOREA_ZONE);
        ApiFootballSyncStatus status = statusByKey(provider.statusKey(), provider.displayName(), now);
        status.recordProviderFailure(provider.displayName(), provider.name(), operation,
                exception.getCategory().name(), exception.getHttpStatus(), attemptCount, now,
                safeMessage(exception.getMessage()));
        apiFootballSyncStatusRepository.save(status);
    }

    private ApiFootballSyncStatus status(String syncKey, String displayName, Integer season, LocalDateTime now) {
        return statusByKey(syncKey(syncKey, season), displayName(displayName, season), now);
    }

    private ApiFootballSyncStatus statusByKey(String syncKey, String displayName, LocalDateTime now) {
        return apiFootballSyncStatusRepository.findById(syncKey)
                .orElseGet(() -> ApiFootballSyncStatus.builder()
                        .syncKey(syncKey)
                        .displayName(displayName)
                        .lastSyncedAt(now)
                        .failureCount(0)
                        .build());
    }

    private String syncKey(String syncKey, Integer season) {
        return season == null ? syncKey : "%s:%d".formatted(syncKey, season);
    }

    private String displayName(String displayName, Integer season) {
        return season == null ? displayName : "%s %d".formatted(displayName, season);
    }

    private String errorMessage(Exception exception) {
        if (exception == null) {
            return null;
        }
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return safeMessage(message);
    }

    private String safeMessage(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
