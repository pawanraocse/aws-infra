package com.learning.systemtests;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.learning.systemtests.config.TestConfig.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * Basic health check tests for core services.
 * These should pass before running any other tests.
 * 
 * <p>
 * Note: When checking services through Gateway, the JWT filter applies.
 * We hit actuator endpoints directly to avoid auth requirements.
 * </p>
 */
class HealthCheckIT extends BaseSystemTest {

    @Test
    @DisplayName("Gateway service is healthy")
    void verifyGatewayIsHealthy() {
        given()
                .when()
                .get("/actuator/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));

        log.info("✅ Gateway is healthy");
    }

    @Test
    @DisplayName("Eureka server is healthy")
    void verifyEurekaIsHealthy() {
        // Connect directly to the running Eureka server
        given()
                .baseUri(EUREKA_URL)
                .when()
                .get("/actuator/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));

        log.info("✅ Eureka is healthy");
    }

    @Test
    @DisplayName("Auth service is healthy (direct)")
    void verifyAuthServiceIsHealthy() {
        // Hit auth-service directly to avoid Gateway JWT filter
        given()
                .baseUri("http://localhost:8081")
                .when()
                .get("/auth/actuator/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));

        log.info("✅ Auth service is healthy");
    }

    @Test
    @DisplayName("Platform service is healthy (direct)")
    void verifyPlatformServiceIsHealthy() {
        // Hit platform-service directly to avoid Gateway JWT filter
        given()
                .baseUri("http://localhost:8083")
                .when()
                .get("/platform/actuator/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));

        log.info("✅ Platform service is healthy");
    }

    @Test
    @DisplayName("Backend service is healthy (direct)")
    void verifyBackendServiceIsHealthy() {
        // Hit backend-service directly
        given()
                .baseUri("http://localhost:8082")
                .when()
                .get("/actuator/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));

        log.info("✅ Backend service is healthy");
    }
}
