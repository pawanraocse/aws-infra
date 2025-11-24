# System Tests Module

This module contains End-to-End (E2E) integration tests for the AWS-Infra project.

## Test Strategy

These tests connect to a **running** `docker-compose` environment on `localhost`. They use the `maven-failsafe-plugin` and **do not run during normal builds**.

### Why This Approach?

*   **Doesn't Break Normal Builds**: Tests are skipped during `mvn package` by default
*   **Tests the Real Setup**: Uses your exact production `docker-compose.yml` configuration
*   **Tests with Real AWS**: Uses your actual AWS SSM credentials (from the running containers)
*   **CI/CD Friendly**: Can be run explicitly in CI/CD pipelines with the `-Psystem-tests` profile

## Prerequisites

*   Docker must be running
*   Java 21+
*   **A test user in AWS Cognito** with known credentials
*   **IMPORTANT**: You must have the environment running before executing tests

## Setup Test User

### Option 1: Environment Variables (Recommended)
Set these environment variables before running tests:

```bash
export TEST_USER_EMAIL="test@example.com"
export TEST_USER_PASSWORD="Test123!"
```

### Option 2: Cognito Console
1. Go to AWS Cognito Console
2. Create a test user in your user pool
3. Note the credentials
4. Set the environment variables above

## Running Tests

### Normal Build (Skips System Tests)

```bash
# This will NOT run system tests
mvn clean package

# Build succeeds even if docker-compose is not running
```

### Running System Tests

#### Step 1: Set Test User Credentials

```bash
export TEST_USER_EMAIL="your-test-user@example.com"
export TEST_USER_PASSWORD="YourTestPassword123!"
```

#### Step 2: Start the Environment

```bash
docker-compose up -d
```

Wait for all services to become healthy (check with `docker ps`).

#### Step 3: Run System Tests

```bash
# Run all system tests
mvn verify -Psystem-tests

# Run from system-tests module only
mvn verify -pl system-tests -Psystem-tests

# Run a specific test class
mvn verify -pl system-tests -Psystem-tests -Dit.test=CreateEntryIT

# Run a specific test method
mvn verify -pl system-tests -Psystem-tests -Dit.test=HealthCheckIT#verifyGatewayIsHealthy
```

#### Step 4: (Optional) Stop the Environment

```bash
docker-compose down
```

## Test Structure

### Base Classes
*   **`BaseSystemTest`**: Base class that configures RestAssured to connect to `localhost:8080` (Gateway)
*   **`AuthHelper`**: Utility class that handles automated login and JWT token retrieval

### Test Classes

#### `HealthCheckIT`
Basic health check tests:
*   Verifies Gateway is UP
*   Verifies Eureka is UP

#### `CreateEntryIT`
Full CRUD workflow tests with automated authentication:
*   **Create**: POST entry and verify response
*   **Read**: GET entry by ID
*   **Update**: PUT entry with new data
*   **Delete**: DELETE entry and verify it's gone
*   **Security**: Verify unauthenticated requests are rejected

## Automated Authentication

The `AuthHelper` class automatically:
1. Calls `POST /auth/login` with test credentials
2. Extracts the JWT access token from the response
3. Provides it to tests via `AuthHelper.getAccessToken()`

This means tests don't need to manually handle authentication - it's done automatically in `@BeforeAll`.

## Writing New Tests

To add a new E2E test:

```java
public class MyNewIT extends BaseSystemTest {

    private static String jwtToken;

    @BeforeAll
    static void authenticate() {
        jwtToken = AuthHelper.getAccessToken();
    }

    @Test
    void myTest() {
        given()
            .header("Authorization", "Bearer " + jwtToken)
            .when()
                .get("/api/v1/my-endpoint")
            .then()
                .statusCode(200);
    }
}
```

**Important**: Name your test class with the `*IT.java` suffix (not `*Test.java`) so it's recognized by the failsafe plugin.

## CI/CD Integration

In your CI/CD pipeline (GitHub Actions, Jenkins, etc.):

```yaml
# Example GitHub Actions
- name: Run System Tests
  run: |
    export TEST_USER_EMAIL=${{ secrets.TEST_USER_EMAIL }}
    export TEST_USER_PASSWORD=${{ secrets.TEST_USER_PASSWORD }}
    docker-compose up -d
    mvn verify -Psystem-tests
    docker-compose down
```

## Troubleshooting

### Connection Refused Errors
*   Ensure `docker-compose up -d` is running
*   Check all services are healthy: `docker ps`
*   Verify ports are not blocked by firewall

### Authentication Failures
*   Verify `TEST_USER_EMAIL` and `TEST_USER_PASSWORD` are set correctly
*   Ensure the test user exists in your Cognito user pool
*   Check the user's status is "CONFIRMED" (not "FORCE_CHANGE_PASSWORD")
*   Verify the password meets Cognito's complexity requirements

### Tests Run During mvn package
*   This should not happen if configured correctly
*   Verify `<skipSystemTests>true</skipSystemTests>` is set in root `pom.xml`
*   Check that you're using failsafe plugin (not surefire) in `system-tests/pom.xml`

### Test Data Pollution
*   Tests use the same `postgres_data` volume as your dev work
*   Consider cleaning up test data after tests or using unique identifiers
*   Future enhancement: Add database cleanup in `@AfterEach` or `@AfterAll`
