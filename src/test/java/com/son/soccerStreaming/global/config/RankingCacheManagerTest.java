package com.son.soccerStreaming.global.config;

import com.son.soccerStreaming.global.exception.CustomException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.SimpleValueWrapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RankingCacheManagerTest {

    private final CacheManager delegate = mock(CacheManager.class);
    private final Cache backingCache = mock(Cache.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();

    private Cache rankingCache(Duration wait, int fallbackPermits) {
        when(delegate.getCache("rankings")).thenReturn(backingCache);
        when(backingCache.getName()).thenReturn("rankings");
        when(redis.opsForValue()).thenReturn(values);
        return new RankingCacheManager(delegate, redis, metrics, Duration.ofSeconds(60), wait,
                fallbackPermits).getCache("rankings");
    }

    @Test
    void ownerWritesComputedValueWithExpiringLock() {
        Cache cache = rankingCache(Duration.ofMillis(250), 2);
        AtomicReference<String> owner = new AtomicReference<>();
        when(values.setIfAbsent(eq("match-vault:ranking-cache:lock:rankings:0:key"), any(),
                eq(Duration.ofSeconds(60)))).thenAnswer(invocation -> {
            owner.set(invocation.getArgument(1));
            return true;
        });
        when(values.get("match-vault:ranking-cache:lock:rankings:0:key"))
                .thenAnswer(invocation -> owner.get());

        assertThat(cache.get("key", () -> "result")).isEqualTo("result");

        verify(backingCache).put("0:key", "result");
        verify(values).setIfAbsent(eq("match-vault:ranking-cache:lock:rankings:0:key"), any(),
                eq(Duration.ofSeconds(60)));
    }

    @Test
    void expiredOwnerDoesNotWriteOrReleaseNewOwnersLock() {
        Cache cache = rankingCache(Duration.ofMillis(250), 2);
        when(values.setIfAbsent(eq("match-vault:ranking-cache:lock:rankings:0:key"), any(),
                eq(Duration.ofSeconds(60)))).thenReturn(true);
        when(values.get("match-vault:ranking-cache:lock:rankings:0:key")).thenReturn("new-owner");

        assertThat(cache.get("key", () -> "old-result")).isEqualTo("old-result");

        verify(backingCache, never()).put(any(), any());
        verify(redis).execute(any(), eq(List.of("match-vault:ranking-cache:lock:rankings:0:key")), any());
        assertThat(metrics.get("ranking.cache.lock_lost").counter().count()).isEqualTo(1);
    }

    @Test
    void waitTimeoutFallsBackWithoutCaching() {
        Cache cache = rankingCache(Duration.ZERO, 1);
        when(values.setIfAbsent(eq("match-vault:ranking-cache:lock:rankings:0:key"), any(),
                eq(Duration.ofSeconds(60)))).thenReturn(false);

        assertThat(cache.get("key", () -> "database-result")).isEqualTo("database-result");

        verify(backingCache, never()).put(any(), any());
        assertThat(metrics.get("ranking.cache.request").tag("outcome", "wait_timeout")
                .counter().count()).isEqualTo(1);
    }

    @Test
    void fallbackConcurrencyIsLimited() {
        Cache cache = rankingCache(Duration.ZERO, 1);
        when(values.setIfAbsent(eq("match-vault:ranking-cache:lock:rankings:0:key"), any(),
                eq(Duration.ofSeconds(60)))).thenReturn(false);

        assertThatThrownBy(() -> cache.get("key", () -> cache.get("key", () -> "nested")))
                .hasCauseInstanceOf(CustomException.class);
    }

    @Test
    void clearAdvancesEpochSoPreviousLoaderCannotRepopulateCurrentKey() {
        Cache cache = rankingCache(Duration.ofMillis(250), 2);
        when(values.get("match-vault:ranking-cache:epoch:rankings")).thenReturn(null, "1");

        cache.get("key");
        cache.clear();
        cache.get("key");

        verify(backingCache).get("0:key");
        verify(backingCache).get("1:key");
        verify(values).increment("match-vault:ranking-cache:epoch:rankings");
        verify(backingCache).clear();
    }

    @Test
    void simultaneousMissesForSameKeyComputeOnlyOnce() throws Exception {
        Cache cache = rankingCache(Duration.ofSeconds(2), 2);
        AtomicReference<String> lockOwner = new AtomicReference<>();
        AtomicReference<String> cachedValue = new AtomicReference<>();
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch contended = new CountDownLatch(1);
        CountDownLatch finishLoad = new CountDownLatch(1);
        when(values.setIfAbsent(eq("match-vault:ranking-cache:lock:rankings:0:key"), any(),
                eq(Duration.ofSeconds(60)))).thenAnswer(invocation -> {
            if (lockOwner.compareAndSet(null, invocation.getArgument(1))) {
                return true;
            }
            contended.countDown();
            return false;
        });
        when(values.get("match-vault:ranking-cache:lock:rankings:0:key"))
                .thenAnswer(invocation -> lockOwner.get());
        when(backingCache.get("0:key")).thenAnswer(invocation -> cachedValue.get() == null
                ? null : new SimpleValueWrapper(cachedValue.get()));
        doAnswer(invocation -> {
            cachedValue.set(invocation.getArgument(1));
            return null;
        }).when(backingCache).put(eq("0:key"), any());

        ExecutorService requests = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = requests.submit(() -> cache.get("key", () -> {
                loads.incrementAndGet();
                loading.countDown();
                if (!finishLoad.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("load did not finish");
                }
                return "result";
            }));
            assertThat(loading.await(5, TimeUnit.SECONDS)).isTrue();
            Future<String> second = requests.submit(() -> cache.get("key", () -> {
                loads.incrementAndGet();
                return "duplicate";
            }));
            assertThat(contended.await(5, TimeUnit.SECONDS)).isTrue();
            finishLoad.countDown();

            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo("result");
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo("result");
            assertThat(loads.get()).isEqualTo(1);
        } finally {
            finishLoad.countDown();
            requests.shutdownNow();
        }
    }
}
