package com.assignment.featureflagsdk;

import lombok.Getter;

import java.time.Duration;

@Getter
public class FeatureFlagsClientConfig {

    private final String baseUrl;
    private final String environment;
    private final Duration cacheTtl;
    private final Duration connectTimeout;
    private final Duration requestTimeout;

    private FeatureFlagsClientConfig(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.environment = builder.environment;
        this.cacheTtl = builder.cacheTtl;
        this.connectTimeout = builder.connectTimeout;
        this.requestTimeout = builder.requestTimeout;
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

        public FeatureFlagsClientConfig build() {
            validate();
            return new FeatureFlagsClientConfig(this);
        }

        private void validate() {
            if (baseUrl == null || baseUrl.isBlank()) {
                throw new IllegalArgumentException("baseUrl must not be null or blank");
            }
            if (environment == null || environment.isBlank()) {
                throw new IllegalArgumentException("environment must not be null or blank");
            }
            if (!environment.equals("dev")
                    && !environment.equals("staging")
                    && !environment.equals("prod")) {
                throw new IllegalArgumentException(
                        "environment must be one of: dev, staging, prod");
            }
            if (cacheTtl == null || cacheTtl.isNegative() || cacheTtl.isZero()) {
                throw new IllegalArgumentException("cacheTtl must be a positive duration");
            }
        }
    }
}