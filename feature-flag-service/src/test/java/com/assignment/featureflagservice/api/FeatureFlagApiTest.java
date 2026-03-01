package com.assignment.featureflagservice.api;

import com.assignment.featureflagservice.BaseIntegrationTest;
import com.assignment.featureflagservice.helpers.TestDataCleaner;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

class FeatureFlagApiTest extends BaseIntegrationTest {

    @Autowired
    private TestDataCleaner cleaner;

    @BeforeEach
    void cleanDatabase() {
        cleaner.cleanAll();
    }

    @Nested
    class CreateFlagApiTests {

        @Test
        void givenValidRequest_whenCreateFlag_thenReturn201WithAllFields() {
            given()
                    .contentType(ContentType.JSON)
                    .header("X-Actor", "test-user")
                    .body("""
                    {
                        "flagKey": "new-checkout",
                        "description": "Enables new checkout flow",
                        "environments": {
                            "dev": true,
                            "staging": false,
                            "prod": false
                        }
                    }
                    """)
                    .when()
                    .post("/api/v1/flags")
                    .then()
                    .statusCode(201)
                    .body("flagKey", equalTo("new-checkout"))
                    .body("description", equalTo("Enables new checkout flow"))
                    .body("environments.dev", equalTo(true))
                    .body("environments.staging", equalTo(false))
                    .body("environments.prod", equalTo(false))
                    .body("version", equalTo(1))
                    .body("createdAt", notNullValue())
                    .body("updatedAt", notNullValue());
        }

        @Test
        void givenDuplicateFlagKey_whenCreateFlag_thenReturn409() {
            String body = """
                {
                    "flagKey": "duplicate-flag",
                    "description": "First",
                    "environments": {"dev": true, "staging": false, "prod": false}
                }
                """;
            given().contentType(ContentType.JSON).body(body)
                    .when().post("/api/v1/flags")
                    .then().statusCode(201);

            given()
                    .contentType(ContentType.JSON)
                    .body(body)
                    .when()
                    .post("/api/v1/flags")
                    .then()
                    .statusCode(409)
                    .body("status", equalTo(409))
                    .body("error", equalTo("FLAG_ALREADY_EXISTS"))
                    .body("message", containsString("duplicate-flag"))
                    .body("timestamp", notNullValue())
                    .body("path", notNullValue());
        }

        @Test
        void givenMissingFlagKey_whenCreateFlag_thenReturn422() {
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                    {
                        "description": "Missing flag key",
                        "environments": {"dev": true, "staging": false, "prod": false}
                    }
                    """)
                    .when()
                    .post("/api/v1/flags")
                    .then()
                    .statusCode(422)
                    .body("status", equalTo(422))
                    .body("error", equalTo("VALIDATION_FAILED"))
                    .body("message", notNullValue())
                    .body("timestamp", notNullValue())
                    .body("path", notNullValue());
        }

        @Test
        void givenMissingDescription_whenCreateFlag_thenReturn422() {
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                    {
                        "flagKey": "my-flag",
                        "environments": {"dev": true, "staging": false, "prod": false}
                    }
                    """)
                    .when()
                    .post("/api/v1/flags")
                    .then()
                    .statusCode(422)
                    .body("status", equalTo(422))
                    .body("error", equalTo("VALIDATION_FAILED"));
        }

        @Test
        void givenMissingEnvironments_whenCreateFlag_thenReturn422() {
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                    {
                        "flagKey": "my-flag",
                        "description": "Missing environments"
                    }
                    """)
                    .when()
                    .post("/api/v1/flags")
                    .then()
                    .statusCode(422)
                    .body("status", equalTo(422));
        }

        @Test
        void givenNoXActorHeader_whenCreateFlag_thenAuditRecordHasUnknownActor() {
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                    {
                        "flagKey": "actor-test-flag",
                        "description": "Testing actor default",
                        "environments": {"dev": true, "staging": false, "prod": false}
                    }
                    """)
                    .when()
                    .post("/api/v1/flags")
                    .then()
                    .statusCode(201);

            given()
                    .when()
                    .get("/api/v1/flags/actor-test-flag/audit")
                    .then()
                    .statusCode(200)
                    .body("[0].changedBy", equalTo("unknown"));
        }
    }

    @Nested
    class GetFlagApiTests {

        @Test
        void givenExistingFlag_whenGetFlag_thenReturn200WithFlag() {
            given().contentType(ContentType.JSON)
                    .body("""
                    {
                        "flagKey": "get-test-flag",
                        "description": "Get test",
                        "environments": {"dev": true, "staging": true, "prod": false}
                    }
                    """)
                    .post("/api/v1/flags");

            given()
                    .when()
                    .get("/api/v1/flags/get-test-flag")
                    .then()
                    .statusCode(200)
                    .body("flagKey", equalTo("get-test-flag"))
                    .body("version", equalTo(1));
        }

        @Test
        void givenNonExistentFlag_whenGetFlag_thenReturn404() {
            given()
                    .when()
                    .get("/api/v1/flags/non-existent-flag")
                    .then()
                    .statusCode(404)
                    .body("status", equalTo(404))
                    .body("error", equalTo("FLAG_NOT_FOUND"))
                    .body("message", containsString("non-existent-flag"));
        }
    }

    @Nested
    class ListFlagsApiTests {

        @Test
        void givenNoFlags_whenListFlags_thenReturn200WithEmptyArray() {
            given()
                    .when()
                    .get("/api/v1/flags")
                    .then()
                    .statusCode(200)
                    .body("$", hasSize(0));
        }

        @Test
        void givenMultipleFlagsExist_whenListFlags_thenReturnAllFlags() {
            for (String key : new String[]{"flag-one", "flag-two"}) {
                given().contentType(ContentType.JSON)
                        .body(String.format("""
                        {
                            "flagKey": "%s",
                            "description": "Test flag",
                            "environments": {"dev": true, "staging": false, "prod": false}
                        }
                        """, key))
                        .post("/api/v1/flags");
            }

            given()
                    .when()
                    .get("/api/v1/flags")
                    .then()
                    .statusCode(200)
                    .body("$", hasSize(2));
        }
    }

    @Nested
    class UpdateFlagApiTests {

        @Test
        void givenCorrectVersion_whenUpdateFlag_thenReturn200WithIncrementedVersion() {
            given().contentType(ContentType.JSON)
                    .body("""
                    {
                        "flagKey": "update-test-flag",
                        "description": "Original",
                        "environments": {"dev": false, "staging": false, "prod": false}
                    }
                    """)
                    .post("/api/v1/flags");

            given()
                    .contentType(ContentType.JSON)
                    .header("X-Actor", "updater")
                    .body("""
                    {
                        "description": "Updated description",
                        "environments": {"dev": true, "staging": true, "prod": false},
                        "version": 1
                    }
                    """)
                    .when()
                    .put("/api/v1/flags/update-test-flag")
                    .then()
                    .statusCode(200)
                    .body("description", equalTo("Updated description"))
                    .body("environments.dev", equalTo(true))
                    .body("environments.staging", equalTo(true))
                    .body("version", equalTo(2));
        }

        @Test
        void givenStaleVersion_whenUpdateFlag_thenReturn409() {
            given().contentType(ContentType.JSON)
                    .body("""
                    {
                        "flagKey": "stale-version-flag",
                        "description": "Original",
                        "environments": {"dev": false, "staging": false, "prod": false}
                    }
                    """)
                    .post("/api/v1/flags");

            given()
                    .contentType(ContentType.JSON)
                    .body("""
                    {
                        "description": "Updated",
                        "environments": {"dev": true, "staging": false, "prod": false},
                        "version": 99
                    }
                    """)
                    .when()
                    .put("/api/v1/flags/stale-version-flag")
                    .then()
                    .statusCode(409)
                    .body("status", equalTo(409))
                    .body("error", equalTo("VERSION_CONFLICT"))
                    .body("message", containsString("stale-version-flag"));
        }

        @Test
        void givenConcurrentUpdates_whenBothSendSameVersion_thenOnlyOneSucceeds()
                throws InterruptedException {

            given().contentType(ContentType.JSON)
                    .body("""
                    {
                        "flagKey": "concurrent-flag",
                        "description": "Original",
                        "environments": {"dev": false, "staging": false, "prod": false}
                    }
                    """)
                    .post("/api/v1/flags");

            String updateBody = """
                {
                    "description": "Concurrent update",
                    "environments": {"dev": true, "staging": false, "prod": false},
                    "version": 1
                }
                """;

            int[] statusCodes = new int[2];
            Thread t1 = new Thread(() -> {
                statusCodes[0] = given()
                        .contentType(ContentType.JSON)
                        .body(updateBody)
                        .when()
                        .put("/api/v1/flags/concurrent-flag")
                        .then()
                        .extract()
                        .statusCode();
            });

            Thread t2 = new Thread(() -> {
                statusCodes[1] = given()
                        .contentType(ContentType.JSON)
                        .body(updateBody)
                        .when()
                        .put("/api/v1/flags/concurrent-flag")
                        .then()
                        .extract()
                        .statusCode();
            });

            t1.start();
            t2.start();
            t1.join();
            t2.join();

            int successCount = 0;
            int conflictCount = 0;
            for (int code : statusCodes) {
                if (code == 200) successCount++;
                if (code == 409) conflictCount++;
            }

            assertThat(successCount).isEqualTo(1);
            assertThat(conflictCount).isEqualTo(1);
        }

        @Test
        void givenMissingVersion_whenUpdateFlag_thenReturn422() {
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                    {
                        "description": "Missing version",
                        "environments": {"dev": true, "staging": false, "prod": false}
                    }
                    """)
                    .when()
                    .put("/api/v1/flags/any-flag")
                    .then()
                    .statusCode(422)
                    .body("status", equalTo(422))
                    .body("error", equalTo("VALIDATION_FAILED"));
        }

        @Test
        void givenNonExistentFlag_whenUpdateFlag_thenReturn404() {
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                    {
                        "description": "Update",
                        "environments": {"dev": true, "staging": false, "prod": false},
                        "version": 1
                    }
                    """)
                    .when()
                    .put("/api/v1/flags/ghost-flag")
                    .then()
                    .statusCode(404)
                    .body("status", equalTo(404))
                    .body("error", equalTo("FLAG_NOT_FOUND"));
        }
    }

    @Nested
    class DeleteFlagApiTests {

        @Test
        void givenExistingFlag_whenDeleteFlag_thenReturn204AndFlagNoLongerExists() {
            given().contentType(ContentType.JSON)
                    .body("""
                    {
                        "flagKey": "delete-me",
                        "description": "To be deleted",
                        "environments": {"dev": true, "staging": false, "prod": false}
                    }
                    """)
                    .post("/api/v1/flags");

            given()
                    .header("X-Actor", "deleter")
                    .when()
                    .delete("/api/v1/flags/delete-me")
                    .then()
                    .statusCode(204);

            given()
                    .when()
                    .get("/api/v1/flags/delete-me")
                    .then()
                    .statusCode(404);
        }

        @Test
        void givenNonExistentFlag_whenDeleteFlag_thenReturn404() {
            given()
                    .when()
                    .delete("/api/v1/flags/ghost-flag")
                    .then()
                    .statusCode(404)
                    .body("status", equalTo(404))
                    .body("error", equalTo("FLAG_NOT_FOUND"));
        }
    }

    @Nested
    class AuditHistoryApiTests {

        @Test
        void givenCreatedFlag_whenGetAuditHistory_thenReturn200WithCreatedEntry() {
            given().contentType(ContentType.JSON)
                    .header("X-Actor", "creator")
                    .body("""
                    {
                        "flagKey": "audit-flag",
                        "description": "Audit test",
                        "environments": {"dev": true, "staging": false, "prod": false}
                    }
                    """)
                    .post("/api/v1/flags");

            given()
                    .when()
                    .get("/api/v1/flags/audit-flag/audit")
                    .then()
                    .statusCode(200)
                    .body("$", hasSize(1))
                    .body("[0].action", equalTo("CREATED"))
                    .body("[0].changedBy", equalTo("creator"))
                    .body("[0].version", equalTo(1))
                    .body("[0].beforeState", nullValue())
                    .body("[0].afterState", notNullValue())
                    .body("[0].changedAt", notNullValue());
        }

        @Test
        void givenUpdatedFlag_whenGetAuditHistory_thenReturnMostRecentFirst() {
            given().contentType(ContentType.JSON)
                    .body("""
                    {
                        "flagKey": "audit-order-flag",
                        "description": "Original",
                        "environments": {"dev": false, "staging": false, "prod": false}
                    }
                    """)
                    .post("/api/v1/flags");

            given().contentType(ContentType.JSON)
                    .body("""
                    {
                        "description": "Updated",
                        "environments": {"dev": true, "staging": false, "prod": false},
                        "version": 1
                    }
                    """)
                    .put("/api/v1/flags/audit-order-flag");

            given()
                    .when()
                    .get("/api/v1/flags/audit-order-flag/audit")
                    .then()
                    .statusCode(200)
                    .body("$", hasSize(2))
                    .body("[0].action", equalTo("UPDATED"))
                    .body("[0].beforeState", notNullValue())
                    .body("[0].afterState", notNullValue())
                    .body("[1].action", equalTo("CREATED"));
        }

        @Test
        void givenDeletedFlag_whenGetAuditHistory_thenAuditHistoryStillAccessible() {
            given().contentType(ContentType.JSON)
                    .body("""
                    {
                        "flagKey": "deleted-audit-flag",
                        "description": "Will be deleted",
                        "environments": {"dev": true, "staging": false, "prod": false}
                    }
                    """)
                    .post("/api/v1/flags");
            given().delete("/api/v1/flags/deleted-audit-flag");
            given()
                    .when()
                    .get("/api/v1/flags/deleted-audit-flag/audit")
                    .then()
                    .statusCode(200)
                    .body("$", hasSize(2))
                    .body("[0].action", equalTo("DELETED"))
                    .body("[0].beforeState", notNullValue())
                    .body("[0].afterState", nullValue())
                    .body("[1].action", equalTo("CREATED"));
        }

        @Test
        void givenNonExistentFlag_whenGetAuditHistory_thenReturn404() {
            given()
                    .when()
                    .get("/api/v1/flags/ghost-flag/audit")
                    .then()
                    .statusCode(404)
                    .body("status", equalTo(404));
        }
    }
}