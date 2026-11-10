package com.son.soccerStreaming.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Invalidates ranking entries only after their source data is committed. */
@Service
@Slf4j
public class RankingCacheInvalidationService {

    private final CacheManager rankingCacheManager;
    private final Set<Integer> pendingSeasons = ConcurrentHashMap.newKeySet();

    public RankingCacheInvalidationService(
            @Qualifier(RedisCacheConfig.RANKINGS_CACHE_MANAGER) CacheManager rankingCacheManager) {
        this.rankingCacheManager = rankingCacheManager;
    }

    public void invalidateAfterCommit(int season) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            invalidate(season);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                invalidate(season);
            }
        });
    }

    @Scheduled(fixedDelayString = "${ranking.cache.invalidation-retry-ms:10000}")
    public void retryFailedInvalidations() {
        for (Integer season : pendingSeasons) {
            invalidate(season);
        }
    }

    private void invalidate(int season) {
        try {
            if (rankingCacheManager instanceof RankingCacheManager manager) {
                manager.invalidatePlayerRankingsForSeason(season);
            } else {
                rankingCacheManager.getCache(RedisCacheConfig.LEAGUE_PLAYER_RANKINGS_CACHE).clear();
                rankingCacheManager.getCache(RedisCacheConfig.TEAM_PLAYER_RANKINGS_CACHE).clear();
            }
            pendingSeasons.remove(season);
        } catch (RuntimeException exception) {
            pendingSeasons.add(season);
            log.warn("Player ranking cache invalidation will retry. season={}", season, exception);
        }
    }
}
