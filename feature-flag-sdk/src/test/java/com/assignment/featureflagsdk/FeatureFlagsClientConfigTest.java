package com.assignment.featureflagsdk;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeatureFlagsClientConfigTest {

    @Nested
    class ValidConfigTests {

        @Test
        void givenAllValidFields_whenBuild_thenConfigCreatedWithCorrectValues() {
            FeatureFlagsClientConfig config = FeatureFlagsClientConfig.builder()
                    .baseUrl("http://localhost:8080")
                    .environment("prod")
                    .cacheTtl(Duration.ofSeconds(60))
                    .connectTimeout(Duration.ofSeconds(10))
                    .requestTimeout(Duration.ofSeconds(5))
                    .build();

            assertThat(config.getBaseUrl()).isEqualTo("http://localhost:8080");
            assertThat(config.getEnvironment()).isEqualTo("prod");
            assertThat(config.getCacheTtl()).isEqualTo(Duration.ofSeconds(60));
            assertThat(config.getConnectTimeout()).isEqualTo(Duration.ofSeconds(10));
            assertThat(config.getRequestTimeout()).isEqualTo(Duration.ofSeconds(5));
        }

        @Test
        void givenOnlyRequiredFields_whenBuild_thenDefaultsApplied() {
            FeatureFlagsClientConfig config = FeatureFlagsClientConfig.builder()
                    .baseUrl("http://localhost:8080")
                    .environment("dev")
                    .build();

            assertThat(config.getCacheTtl()).isEqualTo(Duration.ofSeconds(30));
            assertThat(config.getConnectTimeout()).isEqualTo(Duration.ofSeconds(5));
            assertThat(config.getRequestTimeout()).isEqualTo(Duration.ofSeconds(3));
        }

        @ParameterizedTest
        @ValueSource(strings = {"dev", "staging", "prod"})
        void givenValidEnvironment_whenBuild_thenConfigCreatedSuccessfully(String environment) {
            FeatureFlagsClientConfig config = FeatureFlagsClientConfig.builder()
                    .baseUrl("http://localhost:8080")
                    .environment(environment)
                    .build();

            assertThat(config.getEnvironment()).isEqualTo(environment);
        }
    }

    @Nested
    class BaseUrlValidationTests {

        @Test
        void givenNullBaseUrl_whenBuild_thenThrowIllegalArgumentException() {
            assertThatThrownBy(() -> FeatureFlagsClientConfig.builder()
                    .baseUrl(null)
                    .environment("prod")
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("baseUrl");
        }

        @Test
        void givenBlankBaseUrl_whenBuild_thenThrowIllegalArgumentException() {
            assertThatThrownBy(() -> FeatureFlagsClientConfig.builder()
                    .baseUrl("   ")
                    .environment("prod")
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("baseUrl");
        }
    }

    @Nested
    class EnvironmentValidationTests {

        @Test
        void givenNullEnvironment_whenBuild_thenThrowIllegalArgumentException() {
            assertThatThrownBy(() -> FeatureFlagsClientConfig.builder()
                    .baseUrl("http://localhost:8080")
                    .environment(null)
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("environment");
        }

        @ParameterizedTest
        @ValueSource(strings = {"production", "test", "DEV", "PROD", "Development", ""})
        void givenInvalidEnvironmentValue_whenBuild_thenThrowIllegalArgumentException(
                String invalidEnv) {
            assertThatThrownBy(() -> FeatureFlagsClientConfig.builder()
                    .baseUrl("http://localhost:8080")
                    .environment(invalidEnv)
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("environment");
        }
    }

    @Nested
    class CacheTtlValidationTests {

        @Test
        void givenNullCacheTtl_whenBuild_thenThrowIllegalArgumentException() {
            assertThatThrownBy(() -> FeatureFlagsClientConfig.builder()
                    .baseUrl("http://localhost:8080")
                    .environment("prod")
                    .cacheTtl(null)
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cacheTtl");
        }

        @Test
        void givenNegativeCacheTtl_whenBuild_thenThrowIllegalArgumentException() {
            assertThatThrownBy(() -> FeatureFlagsClientConfig.builder()
                    .baseUrl("http://localhost:8080")
                    .environment("prod")
                    .cacheTtl(Duration.ofSeconds(-1))
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cacheTtl");
        }

        @Test
        void givenZeroCacheTtl_whenBuild_thenThrowIllegalArgumentException() {
            assertThatThrownBy(() -> FeatureFlagsClientConfig.builder()
                    .baseUrl("http://localhost:8080")
                    .environment("prod")
                    .cacheTtl(Duration.ZERO)
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cacheTtl");
        }
    }
}