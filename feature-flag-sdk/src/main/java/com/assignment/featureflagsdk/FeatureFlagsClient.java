package com.assignment.featureflagsdk;

import com.assignment.featureflagsdk.cache.FlagCache;
import com.assignment.featureflagsdk.exception.FeatureFlagException;
import com.assignment.featureflagsdk.http.EvaluationHttpClient;

import java.time.Duration;

public class FeatureFlagsClient {

    private static final boolean FAIL_CLOSED_DEFAULT = false;

    private final EvaluationHttpClient httpClient;
    private final FlagCache cache;

    public FeatureFlagsClient(FeatureFlagsClientConfig config) {
        this.httpClient = new EvaluationHttpClient(
                config.getBaseUrl(),
                config.getEnvironment(),
                config.getConnectTimeout(),
                config.getRequestTimeout());
        this.cache = new FlagCache(config.getCacheTtl());
    }

    FeatureFlagsClient(EvaluationHttpClient httpClient, FlagCache cache) {
        this.httpClient = httpClient;
        this.cache = cache;
    }

    public boolean isEnabled(String flagKey, Context context) {
        FlagCache.CacheEntry cached = cache.get(flagKey);
        if (cached != null && !cache.isExpired(cached)) {
            return cached.enabled();
        }
        try {
            EvaluationResult result = httpClient.evaluate(flagKey, context);
            boolean enabled = result.isEnabled();
            cache.put(flagKey, enabled);
            return enabled;

        } catch (FeatureFlagException e) {
            if (cached != null) {
                return cached.enabled();
            }
            return FAIL_CLOSED_DEFAULT;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String baseUrl;
        private String environment;
        private Duration cacheTtl = Duration.ofSeconds(30);
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration requestTimeout = Duration.ofSeconds(3);

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder environment(String environment) {
            this.environment = environment;
            return this;
        }

        public Builder cacheTtl(Duration cacheTtl) {
            this.cacheTtl = cacheTtl;
            return this;
        }

        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        public FeatureFlagsClient build() {
            FeatureFlagsClientConfig config = FeatureFlagsClientConfig.builder()
                    .baseUrl(baseUrl)
                    .environment(environment)
                    .cacheTtl(cacheTtl)
                    .connectTimeout(connectTimeout)
                    .requestTimeout(requestTimeout)
                    .build();
            return new FeatureFlagsClient(config);
        }
    }
}