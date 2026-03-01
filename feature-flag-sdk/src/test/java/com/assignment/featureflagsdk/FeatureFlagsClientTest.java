package com.assignment.featureflagsdk;

import com.assignment.featureflagsdk.cache.FlagCache;
import com.assignment.featureflagsdk.exception.FeatureFlagException;
import com.assignment.featureflagsdk.http.EvaluationHttpClient;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeatureFlagsClientTest {

    @Mock
    private EvaluationHttpClient httpClient;

    @Mock
    private FlagCache cache;

    private FeatureFlagsClient client;

    @BeforeEach
    void setUp() {
        client = new FeatureFlagsClient(httpClient, cache);
    }

    @Nested
    class CacheBehaviorTests {

        @Test
        void givenValidCacheHit_whenIsEnabled_thenReturnCachedValueWithoutHttpCall() {
            FlagCache.CacheEntry entry = new FlagCache.CacheEntry(true, Instant.now());
            when(cache.get("my-flag")).thenReturn(entry);
            when(cache.isExpired(entry)).thenReturn(false);

            boolean result = client.isEnabled("my-flag", Context.empty());

            assertThat(result).isTrue();
            verify(httpClient, never()).evaluate(any(), any());
        }

        @Test
        void givenCacheMiss_whenIsEnabled_thenCallHttpAndCacheResult() {
            EvaluationResult httpResult = new EvaluationResult();
            httpResult.setEnabled(true);

            when(cache.get("my-flag")).thenReturn(null);
            when(httpClient.evaluate(eq("my-flag"), any())).thenReturn(httpResult);

            boolean result = client.isEnabled("my-flag", Context.empty());

            assertThat(result).isTrue();
            verify(httpClient).evaluate(eq("my-flag"), any());
            verify(cache).put("my-flag", true);
        }

        @Test
        void givenExpiredCacheEntry_whenIsEnabled_thenRefreshFromHttpAndUpdateCache() {
            FlagCache.CacheEntry staleEntry = new FlagCache.CacheEntry(
                    true, Instant.now().minusSeconds(60));
            EvaluationResult freshResult = new EvaluationResult();
            freshResult.setEnabled(false);

            when(cache.get("my-flag")).thenReturn(staleEntry);
            when(cache.isExpired(staleEntry)).thenReturn(true);
            when(httpClient.evaluate(eq("my-flag"), any())).thenReturn(freshResult);

            boolean result = client.isEnabled("my-flag", Context.empty());
            assertThat(result).isFalse();
            verify(httpClient).evaluate(eq("my-flag"), any());
            verify(cache).put("my-flag", false);
        }
    }

    @Nested
    class FailureBehaviorTests {

        @Test
        void givenServiceFailureAndNoCacheEntry_whenIsEnabled_thenFailClosed() {
            when(cache.get("my-flag")).thenReturn(null);
            when(httpClient.evaluate(any(), any()))
                    .thenThrow(new FeatureFlagException("Service unreachable"));

            boolean result = client.isEnabled("my-flag", Context.empty());
            assertThat(result).isFalse();
        }

        @Test
        void givenServiceFailureWithStaleCacheEntry_whenIsEnabled_thenReturnStaleValue() {
            FlagCache.CacheEntry staleEntry = new FlagCache.CacheEntry(
                    true, Instant.now().minusSeconds(60));

            when(cache.get("my-flag")).thenReturn(staleEntry);
            when(cache.isExpired(staleEntry)).thenReturn(true);
            when(httpClient.evaluate(any(), any()))
                    .thenThrow(new FeatureFlagException("Service down"));

            boolean result = client.isEnabled("my-flag", Context.empty());
            assertThat(result).isTrue();
            verify(cache, never()).put(any(), anyBoolean());
        }

        @Test
        void givenServiceFailureWithStaleCacheEntryFalse_whenIsEnabled_thenReturnStaleFalse() {
            FlagCache.CacheEntry staleEntry = new FlagCache.CacheEntry(
                    false, Instant.now().minusSeconds(60));

            when(cache.get("my-flag")).thenReturn(staleEntry);
            when(cache.isExpired(staleEntry)).thenReturn(true);
            when(httpClient.evaluate(any(), any()))
                    .thenThrow(new FeatureFlagException("Service down"));

            boolean result = client.isEnabled("my-flag", Context.empty());

            assertThat(result).isFalse();
        }
    }

    @Nested
    class BuilderTests {

        @Test
        void givenValidBuilderConfig_whenBuild_thenClientCreatedSuccessfully() {
            FeatureFlagsClient builtClient = FeatureFlagsClient.builder()
                    .baseUrl("http://localhost:8080")
                    .environment("prod")
                    .cacheTtl(Duration.ofSeconds(30))
                    .connectTimeout(Duration.ofSeconds(5))
                    .requestTimeout(Duration.ofSeconds(3))
                    .build();

            assertThat(builtClient).isNotNull();
        }

        @Test
        void givenMissingBaseUrl_whenBuild_thenThrowIllegalArgumentException() {
            assertThat(
                    org.assertj.core.api.Assertions.catchThrowable(() ->
                            FeatureFlagsClient.builder()
                                    .environment("prod")
                                    .build()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("baseUrl");
        }

        @Test
        void givenMissingEnvironment_whenBuild_thenThrowIllegalArgumentException() {
            assertThat(
                    org.assertj.core.api.Assertions.catchThrowable(() ->
                            FeatureFlagsClient.builder()
                                    .baseUrl("http://localhost:8080")
                                    .build()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("environment");
        }
    }

    @Nested
    class ThreadSafetyTests {

        @Test
        void givenConcurrentIsEnabledCalls_whenMultipleThreadsCallSameFlag_thenNoExceptions()
                throws InterruptedException {
            WireMockServer wireMock = new WireMockServer(
                    WireMockConfiguration.wireMockConfig().dynamicPort());
            wireMock.start();

            try {
                wireMock.stubFor(post(urlEqualTo("/api/v1/flags/concurrent-flag/evaluate"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                    {"flagKey":"concurrent-flag","enabled":true,
                                     "flagVersion":1,"reason":"enabled","environment":"prod"}
                                    """)));

                FeatureFlagsClient realClient = FeatureFlagsClient.builder()
                        .baseUrl("http://localhost:" + wireMock.port())
                        .environment("prod")
                        .cacheTtl(Duration.ofSeconds(30))
                        .build();

                int threadCount = 30;
                CountDownLatch latch = new CountDownLatch(1);
                CountDownLatch done = new CountDownLatch(threadCount);
                AtomicInteger errors = new AtomicInteger(0);
                AtomicInteger trueCount = new AtomicInteger(0);

                ExecutorService executor = Executors.newFixedThreadPool(threadCount);

                for (int i = 0; i < threadCount; i++) {
                    executor.submit(() -> {
                        try {
                            latch.await();
                            boolean result = realClient.isEnabled(
                                    "concurrent-flag", Context.empty());
                            if (result) trueCount.incrementAndGet();
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
                assertThat(trueCount.get()).isEqualTo(threadCount);

            } finally {
                wireMock.stop();
            }
        }
    }
}