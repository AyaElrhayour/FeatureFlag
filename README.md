# Feature Flag Platform

A production-ready feature flag platform consisting of a REST API service and a Java client SDK. Allows teams to manage feature flags per environment and evaluate them at runtime without redeployment.

---

## Project Structure

```
feature-flag-platform/
├── feature-flag-service/     # Spring Boot REST API
├── feature-flag-sdk/         # Java client library
├── k6/                       # Performance tests
├── docker-compose.yml
└── Makefile
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Runtime | Java 17, Spring Boot 3.2.4 |
| Database | PostgreSQL 16 |
| Migrations | Liquibase |
| API Spec | OpenAPI 3.0 (code generation) |
| Mapping | MapStruct |
| Testing | JUnit 5, Mockito, RestAssured, Testcontainers, WireMock |
| Coverage | JaCoCo |
| Performance | k6 |
| Containerization | Docker, Docker Compose |

---

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.8+
- Docker + Docker Compose

### Run Locally

```bash
# Start Postgres
make up

# Run the service
cd feature-flag-service && mvn spring-boot:run
```

### Run Fully in Docker

```bash
# Build the jar
make build

# Build Docker image and start everything
make up-all
```

The service starts on `http://localhost:8080`.
Swagger UI is available at `http://localhost:8080/swagger-ui.html`.

---

## API Endpoints

### Feature Flags

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/flags` | Create a flag |
| `GET` | `/api/v1/flags` | List all flags |
| `GET` | `/api/v1/flags/{flagKey}` | Get a flag |
| `PUT` | `/api/v1/flags/{flagKey}` | Update a flag |
| `DELETE` | `/api/v1/flags/{flagKey}` | Delete a flag |
| `GET` | `/api/v1/flags/{flagKey}/audit` | Get audit history |

### Evaluation

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/flags/{flagKey}/evaluate` | Evaluate a flag for an environment |

### Headers

`X-Actor` — optional header on write operations. Identifies who made the change. Defaults to `"unknown"` if not provided.

### Environments

Valid values: `dev`, `staging`, `prod`. Case sensitive.

### Example — Create a Flag

```bash
curl -X POST http://localhost:8080/api/v1/flags \
  -H "Content-Type: application/json" \
  -H "X-Actor: ahmed" \
  -d '{
    "flagKey": "new-checkout",
    "description": "Enable new checkout flow",
    "environments": {
      "dev": true,
      "staging": false,
      "prod": false
    }
  }'
```

### Example — Evaluate a Flag

```bash
curl -X POST http://localhost:8080/api/v1/flags/new-checkout/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "environment": "dev",
    "context": {
      "userId": "user-123"
    }
  }'
```

---

## Optimistic Locking

Every update requires the current `version` in the request body. If two requests try to update the same flag concurrently, only the first succeeds. The second gets `409 Conflict` with error code `VERSION_CONFLICT`. This prevents lost updates without database-level locking.

```json
PUT /api/v1/flags/new-checkout
{
  "description": "Updated",
  "environments": { "dev": true, "staging": true, "prod": false },
  "version": 1
}
```

The response returns `version: 2`. The next update must send `version: 2`.

---

## Audit History

Every create, update, and delete is recorded in the audit log. Records are returned most recent first. Audit history is preserved even after a flag is deleted.

```bash
GET /api/v1/flags/new-checkout/audit
```

---

## Error Codes

| HTTP Status | Error Code | Cause |
|---|---|---|
| 400 | `INVALID_ENVIRONMENT` | Invalid or malformed environment value |
| 404 | `FLAG_NOT_FOUND` | Flag does not exist |
| 409 | `FLAG_ALREADY_EXISTS` | Flag key already taken |
| 409 | `VERSION_CONFLICT` | Stale version on update |
| 422 | `VALIDATION_FAILED` | Missing or invalid request fields |
| 500 | `INTERNAL_ERROR` | Unexpected server error |

---

## Java SDK

The SDK is a lightweight Java client that handles HTTP communication, TTL caching, and failure handling. Other Java services add it as a Maven dependency instead of writing raw HTTP calls.

```xml
<dependency>
    <groupId>com.assignment</groupId>
    <artifactId>feature-flag-sdk</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Usage

```java
// Create once at startup as a singleton bean
FeatureFlagsClient client = FeatureFlagsClient.builder()
        .baseUrl("http://feature-flag-service:8080")
        .environment("prod")
        .cacheTtl(Duration.ofSeconds(30))
        .build();

// Use anywhere
boolean enabled = client.isEnabled("new-checkout", Context.user("user-123"));
```

### SDK Behavior

| Situation | Behavior |
|---|---|
| Cache hit within TTL | Returns cached value — no HTTP call |
| Cache miss or expired | Calls service, caches result |
| Service unreachable | Returns `false` (fail-closed) |
| Service down but stale cache exists | Returns stale cached value |
| Invalid environment in config | Throws `IllegalArgumentException` at startup |

Cache TTL defaults to 30 seconds. Connect timeout defaults to 5 seconds. Request timeout defaults to 3 seconds.

---

## Testing

### Run All Tests

```bash
make test-all
```

### Run Service Tests Only

```bash
make test
```

### Run SDK Tests Only

```bash
make test-sdk
```

### Test Layers

| Layer | Tool | What it covers |
|---|---|---|
| Unit — service | JUnit 5 + Mockito | Business logic, audit writing, exception throwing |
| Unit — mapper | JUnit 5 | MapStruct field mapping, enum conversion |
| Unit — SDK | JUnit 5 + Mockito | Cache behavior, fail-closed, stale-on-error |
| Controller | `@WebMvcTest` | HTTP routing, JSON serialization, validation |
| API integration | RestAssured + Testcontainers | Full stack against real Postgres |
| SDK HTTP | WireMock | HTTP client behavior, timeouts, error responses |

Integration tests use Testcontainers — a real Postgres Docker container is started automatically. No manual database setup needed.

---

## Performance Tests

k6 performance tests run against the evaluation endpoint in three scenarios.

```bash
# Start the service first, then run
make perf
```

| Scenario | Virtual Users | Duration | p95 Threshold |
|---|---|---|---|
| Baseline | 1 | 15s | < 100ms |
| Load | 50 | 50s | < 200ms |
| Stress | 200 | 40s | < 500ms |

The test creates a flag in `setup()`, runs all scenarios, then deletes the flag in `teardown()`. It is fully self-contained.

---

## Makefile Reference

```bash
make up            # Start Postgres only
make up-all        # Start Postgres + app (Docker)
make down          # Stop all containers
make clean         # Stop and remove volumes
make build         # Compile jar with Maven
make build-docker  # Build Docker image
make test          # Run service tests
make test-sdk      # Run SDK tests
make test-all      # Run all tests
make perf          # Run k6 against Docker stack
make perf-local    # Run k6 against local app
make logs          # Tail all container logs
make logs-app      # Tail app logs only
make ps            # Show container status
```

---

## Design Decisions

**OpenAPI-first development** — the API contract is defined in YAML before any code is written. Controllers implement generated interfaces, ensuring the implementation always matches the spec.

**Optimistic locking over pessimistic locking** — version-based conflict detection is validated at both the application layer (fast fail with clear error) and the database layer (`@Version` adds `WHERE version = ?` to the SQL). This provides double protection without database-level locks that would limit throughput.

**Audit preserved after deletion** — audit history is stored in a separate table with no foreign key to the flags table. Deleting a flag never deletes its audit trail, which is important for compliance and debugging.

**Fail-closed SDK** — when the feature flag service is unreachable, `isEnabled()` returns `false`. Unknown state defaults to off, which is the safer production default. Stale cache is served during outages to reduce impact.

**Singleton Testcontainers pattern** — a single Postgres container is shared across all integration test classes using a static initializer. This avoids the overhead of starting a new container per test class while maintaining full isolation via `TestDataCleaner` wiping data before each test.
