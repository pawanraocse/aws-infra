# System Tests Module

This module contains End-to-End (E2E) integration tests for the AWS-Infra multi-tenant SaaS platform.

## Test Strategy

These tests connect to a **running** `docker-compose` environment on `localhost`. They use the `maven-failsafe-plugin` and **do not run during normal builds**.

### Key Features

* **Production-Like Testing**: All requests go through Gateway (port 8080) to test real routing
* **Service Readiness Waiting**: Tests wait for services to be healthy before running
* **Centralized Configuration**: All URLs and paths in `TestConfig.java`
* **Database Verification**: `DatabaseHelper` for direct database assertions
* **Retry Logic**: Transient failures are handled with automatic retries
* **Test Data Factory**: Clean, unique test data generation

## Prerequisites

* Docker must be running
* Java 21+
* **A test user in AWS Cognito** (optional - tests create their own users)
* **IMPORTANT**: You must have the environment running before executing tests

## Test Coverage

### Core Flows
| Test Class | Description |
|------------|-------------|
| `HealthCheckIT` | Verifies all services are healthy |
| `SignupFlowIT` | B2C personal + B2B organization signup |
| `LoginFlowIT` | Tenant lookup and login |
| `CreateEntryIT` | Full CRUD workflow for entries |

### Authentication & Authorization
| Test Class | Description |
|------------|-------------|
| `ForgotPasswordFlowIT` | Password reset flow |
| `DeleteAccountFlowIT` | Account deletion with tenant cleanup |
| `InvitationFlowIT` | User invitation flow |
| `RoleBasedAccessIT` | RBAC enforcement tests |

### Multi-Tenancy
| Test Class | Description |
|------------|-------------|
| `TenantIsolationIT` | Data isolation between tenants |
| `PermissionFlowIT` | DB verification for tenant provisioning |
| `MultiAccountFlowIT` | Same email with multiple tenants |

## Running Tests

### Normal Build (Skips System Tests)

```bash
# This will NOT run system tests
mvn clean package

# Build succeeds even if docker-compose is not running
```

### Running System Tests

#### Step 1: Start the Environment

```bash
docker-compose up -d

# Wait for all services to be healthy
docker ps
```

#### Step 2: Run System Tests

```bash
# Run all system tests
mvn verify -Psystem-tests -pl system-tests

# Run a specific test class
mvn verify -Psystem-tests -pl system-tests -Dit.test=SignupFlowIT

# Run a specific test method
mvn verify -Psystem-tests -pl system-tests -Dit.test=HealthCheckIT#verifyGatewayIsHealthy

# Run with debug output
mvn verify -Psystem-tests -pl system-tests -Dorg.slf4j.simpleLogger.defaultLogLevel=DEBUG
```

#### Step 3: (Optional) Stop the Environment

```bash
docker-compose down
```

## Project Structure

```
system-tests/
├── src/test/java/com/learning/systemtests/
│   ├── BaseSystemTest.java          # Base class with service readiness waiting
│   ├── HealthCheckIT.java           # Health checks for all services
│   ├── CreateEntryIT.java           # Entry CRUD tests
│   ├── config/
│   │   └── TestConfig.java          # Centralized configuration
│   ├── util/
│   │   ├── AuthHelper.java          # Authentication utilities
│   │   ├── DatabaseHelper.java      # Database verification & cleanup
│   │   └── TestDataFactory.java     # Test data generation
│   └── auth/
│       ├── SignupFlowIT.java        # Signup tests
│       ├── LoginFlowIT.java         # Login/tenant lookup tests
│       ├── ForgotPasswordFlowIT.java # Password reset tests
│       ├── DeleteAccountFlowIT.java  # Account deletion tests
│       ├── InvitationFlowIT.java    # Invitation tests
│       ├── RoleBasedAccessIT.java   # RBAC tests
│       ├── TenantIsolationIT.java   # Multi-tenant isolation tests
│       ├── PermissionFlowIT.java    # Permission verification tests
│       └── MultiAccountFlowIT.java  # Multi-account per email tests
└── pom.xml
```

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `GATEWAY_URL` | `http://localhost:8080` | Gateway service URL |
| `EUREKA_URL` | `http://localhost:8761` | Eureka service URL |
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `awsinfra` | Database name |
| `DB_USER` | `postgres` | Database user |
| `DB_PASSWORD` | `postgres` | Database password |

### API Paths (via TestConfig)

| Constant | Path | Service |
|----------|------|---------|
| `AUTH_API` | `/auth/api/v1/auth` | Auth service |
| `INVITATION_API` | `/auth/api/v1/invitations` | Auth service |
| `ROLES_API` | `/auth/api/v1/roles` | Auth service |
| `ENTRIES_API` | `/api/v1/entries` | Backend service |
| `PLATFORM_API` | `/platform/api/v1` | Platform service |

## Writing New Tests

### Basic Test Class

```java
public class MyNewIT extends BaseSystemTest {

    @Test
    void myTest() {
        AuthHelper.UserCredentials creds = AuthHelper.signup();
        String token = AuthHelper.login(creds.email(), creds.password());

        given()
            .header("Authorization", "Bearer " + token)
            .when()
                .get(ENTRIES_API)
            .then()
                .statusCode(200);
    }
}
```

**Important**: Name your test class with the `*IT.java` suffix (not `*Test.java`) so it's recognized by the failsafe plugin.

### Using Test Data Factory

```java
// Random email
String email = TestDataFactory.randomEmail();

// Personal signup request
PersonalSignupRequest request = TestDataFactory.personalSignup();

// Organization signup request
OrganizationSignupRequest orgRequest = TestDataFactory.orgSignup("My Company");

// Random entry JSON
String entryJson = TestDataFactory.randomEntryJson();
```

### Database Verification

```java
// Check tenant exists
boolean exists = DatabaseHelper.tenantExists(tenantId);

// Check membership
boolean hasMembership = DatabaseHelper.membershipExists(email, tenantId);

// Check table in tenant DB
boolean tableExists = DatabaseHelper.tableExistsInTenantDb(tenantId, "roles");
```

## CI/CD Integration

In your CI/CD pipeline (GitHub Actions, Jenkins, etc.):

```yaml
# Example GitHub Actions
- name: Start Services
  run: docker-compose up -d

- name: Wait for Services
  run: |
    sleep 60
    docker ps

- name: Run System Tests
  run: mvn verify -Psystem-tests -pl system-tests

- name: Stop Services
  if: always()
  run: docker-compose down
```

## Troubleshooting

### Connection Refused Errors
* Ensure `docker-compose up -d` is running
* Check all services are healthy: `docker ps`
* Verify Gateway is ready: `curl http://localhost:8080/actuator/health`

### Authentication Failures
* Check AWS credentials are configured
* Verify Cognito User Pool is accessible
* Check the `AWS_REGION` environment variable

### Tests Run During mvn package
* This should not happen if configured correctly
* Verify `<skipSystemTests>true</skipSystemTests>` is set in root `pom.xml`
* Check that you're using failsafe plugin (not surefire) in `system-tests/pom.xml`

### Test Data Pollution
* Tests use the same `postgres_data` volume as your dev work
* Tests create unique users with random emails to avoid conflicts
* Use `DatabaseHelper.cleanupTestTenant()` for explicit cleanup if needed

---

*Last Updated: 2025-12-22*
