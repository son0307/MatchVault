package com.son.soccerStreaming.global.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RankingCacheInvalidationServiceTest {

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void invalidatesOnlyAfterCommit() {
        RankingCacheManager manager = mock(RankingCacheManager.class);
        RankingCacheInvalidationService service = new RankingCacheInvalidationService(manager);
        TransactionSynchronizationManager.initSynchronization();

        service.invalidateAfterCommit(2026);
        verify(manager, never()).invalidatePlayerRankingsForSeason(2026);
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
        verify(manager).invalidatePlayerRankingsForSeason(2026);
    }

    @Test
    void rollbackLeavesCacheUntouched() {
        RankingCacheManager manager = mock(RankingCacheManager.class);
        RankingCacheInvalidationService service = new RankingCacheInvalidationService(manager);
        TransactionSynchronizationManager.initSynchronization();

        service.invalidateAfterCommit(2026);
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }
        verify(manager, never()).invalidatePlayerRankingsForSeason(2026);
    }
}
