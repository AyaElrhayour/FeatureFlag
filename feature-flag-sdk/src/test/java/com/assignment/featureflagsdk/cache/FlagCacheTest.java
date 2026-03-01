package com.assignment.featureflagsdk.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class FlagCacheTest {

    private FlagCache cache;

    @BeforeEach
    void setUp() {
        cache = new FlagCache(Duration.ofSeconds(30));
    }

    @Nested
    class GetTests {

        @Test
        void givenUnknownKey_whenGet_thenReturnNull() {
            assertThat(cache.get("unknown-flag")).isNull();
        }

        @Test
        void givenCachedEntry_whenGetWithinTtl_thenReturnEntry() {
            cache.put("my-flag", true);

            FlagCache.CacheEntry entry = cache.get("my-flag");

            assertThat(entry).isNotNull();
            assertThat(entry.enabled()).isTrue();
            assertThat(entry.cachedAt()).isNotNull();
        }

        @Test
        void givenCachedFalseValue_whenGet_thenReturnFalseEntry() {
            cache.put("disabled-flag", false);

            FlagCache.CacheEntry entry = cache.get("disabled-flag");

            assertThat(entry).isNotNull();
            assertThat(entry.enabled()).isFalse();
        }
    }

    @Nested
    class ExpiryTests {

        @Test
        void givenEntryWithinTtl_whenIsExpired_thenReturnFalse() {
            cache.put("my-flag", true);
            FlagCache.CacheEntry entry = cache.get("my-flag");

            assertThat(cache.isExpired(entry)).isFalse();
        }

        @Test
        void givenEntryBeyondTtl_whenIsExpired_thenReturnTrue() throws InterruptedException {
            FlagCache shortTtlCache = new FlagCache(Duration.ofMillis(50));
            shortTtlCache.put("my-flag", true);

            Thread.sleep(100);

            FlagCache.CacheEntry entry = shortTtlCache.get("my-flag");
            assertThat(shortTtlCache.isExpired(entry)).isTrue();
        }
    }

    @Nested
    class InvalidateTests {

        @Test
        void givenCachedEntry_whenInvalidate_thenEntryRemoved() {
            cache.put("my-flag", true);
            cache.invalidate("my-flag");

            assertThat(cache.get("my-flag")).isNull();
        }

        @Test
        void givenMultipleEntries_whenInvalidateOne_thenOnlyThatEntryRemoved() {
            cache.put("flag-one", true);
            cache.put("flag-two", false);

            cache.invalidate("flag-one");

            assertThat(cache.get("flag-one")).isNull();
            assertThat(cache.get("flag-two")).isNotNull();
        }

        @Test
        void givenMultipleEntries_whenInvalidateAll_thenAllEntriesRemoved() {
            cache.put("flag-one", true);
            cache.put("flag-two", false);
            cache.put("flag-three", true);

            cache.invalidateAll();

            assertThat(cache.get("flag-one")).isNull();
            assertThat(cache.get("flag-two")).isNull();
            assertThat(cache.get("flag-three")).isNull();
        }
    }

    @Nested
    class ThreadSafetyTests {

        @Test
        void givenConcurrentWrites_whenMultipleThreadsPut_thenNoConcurrentModificationException()
                throws InterruptedException {

            int threadCount = 20;
            CountDownLatch latch = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threadCount);
            AtomicInteger errors = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final String flagKey = "flag-" + i;
                executor.submit(() -> {
                    try {
                        latch.await();
                        cache.put(flagKey, true);
                        cache.get(flagKey);
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }

            latch.countDown();
            done.await();
            executor.shutdown();

            assertThat(errors.get()).isZero();
        }

        @Test
        void givenConcurrentReadsAndWrites_whenAccessingSameKey_thenNoDataCorruption()
                throws InterruptedException {

            int threadCount = 50;
            CountDownLatch latch = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threadCount);
            AtomicInteger errors = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final boolean value = i % 2 == 0;
                executor.submit(() -> {
                    try {
                        latch.await();
                        cache.put("shared-flag", value);
                        FlagCache.CacheEntry entry = cache.get("shared-flag");
                        if (entry == null) errors.incrementAndGet();
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }

            latch.countDown();
            done.await();
            executor.shutdown();

            assertThat(errors.get()).isZero();
        }
    }
}