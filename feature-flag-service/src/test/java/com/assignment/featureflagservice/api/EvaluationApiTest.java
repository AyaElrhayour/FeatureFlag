package com.assignment.featureflagservice.api;

import com.assignment.featureflagservice.BaseIntegrationTest;
import com.assignment.featureflagservice.helpers.TestDataCleaner;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

class EvaluationApiTest extends BaseIntegrationTest {

    @Autowired
    private TestDataCleaner cleaner;

    @BeforeEach
    void setUp() {
        cleaner.cleanAll();

        // Create a flag with known per-environment state for all evaluation tests
        given().contentType(ContentType.JSON)
                .body("""
                {
                    "flagKey": "eval-flag",
                    "description": "Evaluation test flag",
                    "environments": {
                        "dev": true,
                        "staging": false,
                        "prod": true
                    }
                }
                """)
                .post("/api/v1/flags");
    }

    // ══════════════════════════════════════
    // EVALUATION — Per Environment
    // ══════════════════════════════════════
    @Nested
    class EnvironmentEvaluationTests {

        @ParameterizedTest
        @CsvSource({
                "dev,     true",
                "staging, false",
                "prod,    true"
        })
        void givenFlagWithPerEnvironmentState_whenEvaluate_thenReturnCorrectEnabledState(
                String environment, boolean expectedEnabled) {

            given()
                    .contentType(ContentType.JSON)
                    .body(String.format("""
                    {
                        "environment": "%s",
                        "context": {"userId": "user-123"}
                    }
                    """, environment))
                    .when()
                    .post("/api/v1/flags/eval-flag/evaluate")
                    .then()
                    .statusCode(200)
                    .body("flagKey", equalTo("eval-flag"))
                    .body("enabled", equalTo(expectedEnabled))
                    .body("flagVersion", equalTo(1))
                    .body("reason", notNullValue())
                    .body("environment", equalTo(environment));
        }
    }

    // ══════════════════════════════════════
    // EVALUATION — Response fields
    // ══════════════════════════════════════
    @Nested
    class EvaluationResponseTests {

        @Test
        void givenEnabledFlag_whenEvaluate_thenReasonMentionsEnabled() {
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                    {"environment": "dev"}
                    """)
                    .when()
                    .post("/api/v1/flags/eval-flag/evaluate")
                    .then()
                    .statusCode(200)
                    .body("enabled", equalTo(true))
                    .body("reason", containsStringIgnoringCase("enabled"));
        }

        @Test
        void givenDisabledFlag_whenEvaluate_thenReasonMentionsDisabled() {
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                    {"environment": "staging"}
                    """)
                    .when()
                    .post("/api/v1/flags/eval-flag/evaluate")
                    .then()
                    .statusCode(200)
                    .body("enabled", equalTo(false))
                    .body("reason", containsStringIgnoringCase("disabled"));
        }

        @Test
        void givenContextWithAttributes_whenEvaluate_thenContextIsAcceptedAndIgnored() {
            // Context is accepted but doesn't affect decision yet
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                    {
                        "environment": "prod",
                        "context": {
                            "userId": "user-456",
                            "attributes": {
                                "region": "us-east",
                                "plan": "premium"
                            }
                        }
                    }
                    """)
                    .when()
                    .post("/api/v1/flags/eval-flag/evaluate")
                    .then()
                    .statusCode(200)
                    .body("enabled", equalTo(true));
        }
    }

    // ══════════════════════════════════════
    // EVALUATION — Error cases
    // ══════════════════════════════════════
    @Nested
    class EvaluationErrorTests {

        @Test
        void givenNonExistentFlag_whenEvaluate_thenReturn404() {
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                    {"environment": "prod"}
                    """)
                    .when()
                    .post("/api/v1/flags/ghost-flag/evaluate")
                    .then()
                    .statusCode(404)
                    .body("status", equalTo(404))
                    .body("error", equalTo("FLAG_NOT_FOUND"));
        }

        @Test
        void givenInvalidEnvironmentValue_whenEvaluate_thenReturn400() {
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                    {"environment": "invalid-env"}
                    """)
                    .when()
                    .post("/api/v1/flags/eval-flag/evaluate")
                    .then()
                    .statusCode(400)
                    .body("status", equalTo(400))
                    .body("error", equalTo("INVALID_ENVIRONMENT"));
        }

        @Test
        void givenMissingEnvironment_whenEvaluate_thenReturn422() {
            given()
                    .contentType(ContentType.JSON)
                    .body("{}")
                    .when()
                    .post("/api/v1/flags/eval-flag/evaluate")
                    .then()
                    .statusCode(422)
                    .body("status", equalTo(422))
                    .body("error", equalTo("VALIDATION_FAILED"));
        }
    }
}