package com.assignment.featureflagsdk.http;

import com.assignment.featureflagsdk.Context;
import com.assignment.featureflagsdk.EvaluationResult;
import com.assignment.featureflagsdk.exception.FeatureFlagException;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluationHttpClientTest {

    private WireMockServer wireMock;
    private EvaluationHttpClient client;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        client = new EvaluationHttpClient(
                "http://localhost:" + wireMock.port(),
                "prod",
                Duration.ofSeconds(5),
                Duration.ofSeconds(3));
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Nested
    class SuccessfulEvaluationTests {

        @Test
        void givenServiceReturns200_whenEvaluate_thenReturnParsedResult() {
            wireMock.stubFor(post(urlEqualTo("/api/v1/flags/my-flag/evaluate"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                {
                                    "flagKey": "my-flag",
                                    "enabled": true,
                                    "flagVersion": 1,
                                    "reason": "Flag is enabled in prod",
                                    "environment": "prod"
                                }
                                """)));

            EvaluationResult result = client.evaluate("my-flag", Context.empty());

            assertThat(result).isNotNull();
            assertThat(result.getFlagKey()).isEqualTo("my-flag");
            assertThat(result.isEnabled()).isTrue();
            assertThat(result.getFlagVersion()).isEqualTo(1);
            assertThat(result.getReason()).isEqualTo("Flag is enabled in prod");
            assertThat(result.getEnvironment()).isEqualTo("prod");
        }

        @Test
        void givenServiceReturnsEnabledFalse_whenEvaluate_thenReturnDisabledResult() {
            wireMock.stubFor(post(urlEqualTo("/api/v1/flags/my-flag/evaluate"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                {
                                    "flagKey": "my-flag",
                                    "enabled": false,
                                    "flagVersion": 2,
                                    "reason": "Flag is disabled in prod",
                                    "environment": "prod"
                                }
                                """)));

            EvaluationResult result = client.evaluate("my-flag", Context.empty());

            assertThat(result.isEnabled()).isFalse();
            assertThat(result.getFlagVersion()).isEqualTo(2);
        }

        @Test
        void givenContextWithUserAndAttributes_whenEvaluate_thenRequestBodyContainsContext() {
            wireMock.stubFor(post(urlEqualTo("/api/v1/flags/my-flag/evaluate"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                {"flagKey":"my-flag","enabled":true,
                                 "flagVersion":1,"reason":"enabled","environment":"prod"}
                                """)));

            client.evaluate("my-flag", Context.user("user-123"));

            wireMock.verify(postRequestedFor(urlEqualTo("/api/v1/flags/my-flag/evaluate"))
                    .withRequestBody(matchingJsonPath("$.environment", equalTo("prod")))
                    .withRequestBody(matchingJsonPath("$.context.userId", equalTo("user-123"))));
        }

        @Test
        void givenNullContext_whenEvaluate_thenRequestStillSentSuccessfully() {
            wireMock.stubFor(post(urlEqualTo("/api/v1/flags/my-flag/evaluate"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                {"flagKey":"my-flag","enabled":true,
                                 "flagVersion":1,"reason":"enabled","environment":"prod"}
                                """)));

            EvaluationResult result = client.evaluate("my-flag", null);

            assertThat(result).isNotNull();
            assertThat(result.isEnabled()).isTrue();
        }
    }

    @Nested
    class ErrorResponseTests {

        @Test
        void givenServiceReturns404_whenEvaluate_thenThrowFeatureFlagException() {
            wireMock.stubFor(post(urlEqualTo("/api/v1/flags/ghost-flag/evaluate"))
                    .willReturn(aResponse()
                            .withStatus(404)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                {"status":404,"error":"FLAG_NOT_FOUND",
                                 "message":"Feature flag not found: ghost-flag"}
                                """)));

            assertThatThrownBy(() -> client.evaluate("ghost-flag", Context.empty()))
                    .isInstanceOf(FeatureFlagException.class)
                    .hasMessageContaining("404");
        }

        @Test
        void givenServiceReturns500_whenEvaluate_thenThrowFeatureFlagException() {
            wireMock.stubFor(post(urlEqualTo("/api/v1/flags/my-flag/evaluate"))
                    .willReturn(aResponse()
                            .withStatus(500)
                            .withBody("Internal Server Error")));

            assertThatThrownBy(() -> client.evaluate("my-flag", Context.empty()))
                    .isInstanceOf(FeatureFlagException.class)
                    .hasMessageContaining("500");
        }

        @Test
        void givenServiceUnreachable_whenEvaluate_thenThrowFeatureFlagException() {
            EvaluationHttpClient unreachableClient = new EvaluationHttpClient(
                    "http://localhost:1",
                    "prod",
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(1));

            assertThatThrownBy(() -> unreachableClient.evaluate("my-flag", Context.empty()))
                    .isInstanceOf(FeatureFlagException.class)
                    .hasMessageContaining("my-flag");
        }

        @Test
        void givenServiceTimesOut_whenEvaluate_thenThrowFeatureFlagException() {
            wireMock.stubFor(post(urlEqualTo("/api/v1/flags/my-flag/evaluate"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withFixedDelay(2000) // 2 second delay
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                {"flagKey":"my-flag","enabled":true,
                                 "flagVersion":1,"reason":"enabled","environment":"prod"}
                                """)));

            EvaluationHttpClient shortTimeoutClient = new EvaluationHttpClient(
                    "http://localhost:" + wireMock.port(),
                    "prod",
                    Duration.ofSeconds(5),
                    Duration.ofMillis(500));
            assertThatThrownBy(() -> shortTimeoutClient.evaluate("my-flag", Context.empty()))
                    .isInstanceOf(FeatureFlagException.class);
        }
    }
}