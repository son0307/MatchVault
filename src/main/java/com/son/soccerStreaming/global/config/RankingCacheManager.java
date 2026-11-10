package com.son.soccerStreaming.global.config;

import com.son.soccerStreaming.global.exception.CustomException;
import com.son.soccerStreaming.global.exception.ErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** Bounds waits for ranking cache misses and limits uncached database fallbacks. */
@Slf4j
public class RankingCacheManager implements CacheManager {

    private static final String LOCK_PREFIX = "match-vault:ranking-cache:lock:";
    private static final String EPOCH_PREFIX = "match-vault:ranking-cache:epoch:";
    private static final String SEASON_EPOCH_PREFIX = "match-vault:ranking-cache:season-epoch:";
    private static final long POLL_MILLIS = 50;
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('DEL', KEYS[1]) else return 0 end", Long.class);

    private final CacheManager delegate;
    private final StringRedisTemplate redis;
    private final MeterRegistry metrics;
    private final Duration lockTtl;
    private final Duration maxWait;
    private final Semaphore fallbackPermits;
    private final ConcurrentHashMap<String, Cache> caches = new ConcurrentHashMap<>();

    public RankingCacheManager(CacheManager delegate, StringRedisTemplate redis, MeterRegistry metrics,
                               Duration lockTtl, Duration maxWait, int fallbackPermits) {
        if (lockTtl.isZero() || lockTtl.isNegative() || maxWait.isNegative() || fallbackPermits < 1) {
            throw new IllegalArgumentException("Invalid ranking cache lock settings");
        }
        this.delegate = delegate;
        this.redis = redis;
        this.metrics = metrics;
        this.lockTtl = lockTtl;
        this.maxWait = maxWait;
        this.fallbackPermits = new Semaphore(fallbackPermits);
    }

    @Override
    public Cache getCache(String name) {
        Cache cache = delegate.getCache(name);
        return cache == null ? null : caches.computeIfAbsent(name, ignored -> new RankingCache(cache));
    }

    @Override
    public Collection<String> getCacheNames() {
        return delegate.getCacheNames();
    }

    public void invalidatePlayerRankingsForSeason(int season) {
        redis.opsForValue().increment(SEASON_EPOCH_PREFIX
                + RedisCacheConfig.LEAGUE_PLAYER_RANKINGS_CACHE + ":" + season);
        redis.opsForValue().increment(SEASON_EPOCH_PREFIX
                + RedisCacheConfig.TEAM_PLAYER_RANKINGS_CACHE + ":" + season);
    }

    private class RankingCache implements Cache {
        private final Cache cache;

        private RankingCache(Cache cache) {
            this.cache = cache;
        }

        @Override
        public String getName() {
            return cache.getName();
        }

        @Override
        public Object getNativeCache() {
            return cache.getNativeCache();
        }

        @Override
        public ValueWrapper get(Object key) {
            return cache.get(versionedKey(key));
        }

        @Override
        public <T> T get(Object key, Class<T> type) {
            return cache.get(versionedKey(key), type);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(Object key, Callable<T> loader) {
            long started = System.nanoTime();
            long deadline = started + maxWait.toNanos();
            String cacheKey;
            try {
                cacheKey = versionedKey(key);
            } catch (DataAccessException e) {
                log.warn("Ranking cache version read failed. cacheName={}", getName(), e);
                return fallback(key, loader, "redis_error");
            }
            String lockKey = LOCK_PREFIX + getName() + ":" + cacheKey;
            String owner = UUID.randomUUID().toString();
            boolean contended = false;
            long waitFinished = 0;

            try {
                while (true) {
                    ValueWrapper cached;
                    try {
                        cached = cache.get(cacheKey);
                    } catch (DataAccessException e) {
                        log.warn("Ranking cache read failed. cacheName={}", getName(), e);
                        waitFinished = System.nanoTime();
                        return fallback(key, loader, "redis_error");
                    }
                    if (cached != null) {
                        waitFinished = System.nanoTime();
                        metrics.counter("ranking.cache.request", "cache", getName(),
                                "outcome", "hit").increment();
                        return (T) cached.get();
                    }

                    if (contended && System.nanoTime() >= deadline) {
                        waitFinished = System.nanoTime();
                        return fallback(key, loader, "wait_timeout");
                    }

                    boolean acquired;
                    try {
                        acquired = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lockKey, owner, lockTtl));
                    } catch (DataAccessException e) {
                        log.warn("Ranking cache lock failed. cacheName={}", getName(), e);
                        waitFinished = System.nanoTime();
                        return fallback(key, loader, "redis_error");
                    }
                    if (acquired) {
                        waitFinished = System.nanoTime();
                        try {
                            // Another owner may have filled the entry between the first read and lock acquisition.
                            try {
                                cached = cache.get(cacheKey);
                            } catch (DataAccessException e) {
                                log.warn("Ranking cache recheck failed. cacheName={}", getName(), e);
                                return fallback(key, loader, "redis_error");
                            }
                            if (cached != null) {
                                return (T) cached.get();
                            }
                            T value = load(key, loader, "owner");
                            try {
                                if (owner.equals(redis.opsForValue().get(lockKey))) {
                                    cache.put(cacheKey, value);
                                } else {
                                    metrics.counter("ranking.cache.lock_lost", "cache", getName()).increment();
                                }
                            } catch (DataAccessException e) {
                                log.warn("Ranking cache write or lock check failed. cacheName={}", getName(), e);
                            }
                            return value;
                        } finally {
                            release(lockKey, owner);
                        }
                    }

                    contended = true;
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) {
                        waitFinished = System.nanoTime();
                        return fallback(key, loader, "wait_timeout");
                    }
                    try {
                        TimeUnit.NANOSECONDS.sleep(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(POLL_MILLIS)));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        waitFinished = System.nanoTime();
                        throw new CustomException(ErrorCode.RANKING_CACHE_BUSY);
                    }
                }
            } finally {
                if (contended) {
                    Timer.builder("ranking.cache.wait").tag("cache", getName())
                            .publishPercentileHistogram().register(metrics)
                            .record((waitFinished == 0 ? System.nanoTime() : waitFinished) - started,
                                    TimeUnit.NANOSECONDS);
                }
            }
        }

        private <T> T fallback(Object key, Callable<T> loader, String reason) {
            metrics.counter("ranking.cache.request", "cache", getName(), "outcome", reason).increment();
            if (!fallbackPermits.tryAcquire()) {
                metrics.counter("ranking.cache.fallback_rejected", "cache", getName()).increment();
                throw new CustomException(ErrorCode.RANKING_CACHE_BUSY);
            }
            try {
                return load(key, loader, "fallback");
            } finally {
                fallbackPermits.release();
            }
        }

        private <T> T load(Object key, Callable<T> loader, String path) {
            long started = System.nanoTime();
            try {
                T value = loader.call();
                recordLoad(path, "success", started);
                return value;
            } catch (Exception e) {
                recordLoad(path, "failure", started);
                throw new ValueRetrievalException(key, loader, e);
            }
        }

        private void recordLoad(String path, String outcome, long started) {
            Timer.builder("ranking.cache.load").tag("cache", getName()).tag("path", path)
                    .tag("outcome", outcome).publishPercentileHistogram().register(metrics)
                    .record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
        }

        private void release(String lockKey, String owner) {
            try {
                redis.execute(RELEASE_SCRIPT, List.of(lockKey), owner);
            } catch (DataAccessException e) {
                log.warn("Ranking cache lock release failed. cacheName={}", getName(), e);
            }
        }

        private String versionedKey(Object key) {
            String epoch = redis.opsForValue().get(EPOCH_PREFIX + getName());
            String seasonEpoch = "0";
            String keyText = key.toString();
            int seasonStart = keyText.lastIndexOf(":season:");
            if (seasonStart >= 0) {
                String season = keyText.substring(seasonStart + ":season:".length());
                int suffix = season.indexOf(':');
                if (suffix >= 0) {
                    season = season.substring(0, suffix);
                }
                if (!season.isEmpty() && season.chars().allMatch(Character::isDigit)) {
                    String current = redis.opsForValue().get(
                            SEASON_EPOCH_PREFIX + getName() + ":" + season);
                    seasonEpoch = current == null ? "0" : current;
                }
            }
            return (epoch == null ? "0" : epoch) + ":"
                    + (seasonStart >= 0 ? seasonEpoch + ":" : "") + key;
        }

        @Override
        public void put(Object key, Object value) {
            cache.put(versionedKey(key), value);
        }

        @Override
        public ValueWrapper putIfAbsent(Object key, Object value) {
            return cache.putIfAbsent(versionedKey(key), value);
        }

        @Override
        public void evict(Object key) {
            cache.evict(versionedKey(key));
        }

        @Override
        public void clear() {
            // A loader from the previous epoch may finish after this clear.
            // Its write then uses an unreachable key and cannot repopulate stale data.
            redis.opsForValue().increment(EPOCH_PREFIX + getName());
            cache.clear();
        }
    }
}
