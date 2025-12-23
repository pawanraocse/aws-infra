package com.learning.systemtests;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.learning.systemtests.config.TestConfig.*;
import static org.awaitility.Awaitility.await;

/**
 * Base class for all integration tests.
 * Configures RestAssured and waits for services to be ready.
 */
public abstract class BaseSystemTest {

    protected static final Logger log = LoggerFactory.getLogger(BaseSystemTest.class);

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = GATEWAY_URL;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

        log.info("========================================");
        log.info("System Tests Configuration");
        log.info("========================================");
        log.info("Gateway URL: {}", GATEWAY_URL);
        log.info("Eureka URL: {}", EUREKA_URL);
        log.info("Database: {}:{}/{}", DB_HOST, DB_PORT, DB_NAME);
        log.info("========================================");

        // Wait for Gateway health
        waitForGatewayReady();

        // Wait for Eureka to discover all services
        waitForServiceDiscovery();

        // Additional delay for services to fully stabilize
        log.info("Waiting 5s for services to stabilize...");
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        log.info("✅ All services ready. Starting tests...");
    }

    /**
     * Wait for Gateway to be healthy.
     */
    private static void waitForGatewayReady() {
        log.info("Waiting for Gateway to be ready...");

        await()
                .atMost(60, TimeUnit.SECONDS)
                .pollInterval(2000, TimeUnit.MILLISECONDS)
                .ignoreExceptions()
                .until(() -> {
                    try {
                        Response response = RestAssured.given()
                                .baseUri(GATEWAY_URL)
                                .get("/actuator/health");
                        return response.getStatusCode() == 200 &&
                                "UP".equals(response.jsonPath().getString("status"));
                    } catch (Exception e) {
                        log.debug("Gateway not ready yet: {}", e.getMessage());
                        return false;
                    }
                });

        log.info("✅ Gateway is healthy");
    }

    /**
     * Wait for Eureka to discover auth-service and platform-service.
     * This is critical because auth-service calls platform-service via Eureka.
     */
    private static void waitForServiceDiscovery() {
        log.info("Waiting for Eureka service discovery...");

        await()
                .atMost(90, TimeUnit.SECONDS)
                .pollInterval(3000, TimeUnit.MILLISECONDS)
                .ignoreExceptions()
                .until(() -> {
                    try {
                        Response response = RestAssured.given()
                                .baseUri(EUREKA_URL)
                                .accept("application/json")
                                .get("/eureka/apps");

                        if (response.getStatusCode() != 200) {
                            log.debug("Eureka apps endpoint not ready");
                            return false;
                        }

                        // Check for required services in Eureka registry
                        String body = response.getBody().asString().toLowerCase();
                        boolean hasAuth = body.contains("auth-service");
                        boolean hasPlatform = body.contains("platform-service");
                        boolean hasBackend = body.contains("backend-service");
                        boolean hasGateway = body.contains("gateway-service");

                        log.debug("Service discovery status: auth={}, platform={}, backend={}, gateway={}",
                                hasAuth, hasPlatform, hasBackend, hasGateway);

                        return hasAuth && hasPlatform && hasBackend && hasGateway;
                    } catch (Exception e) {
                        log.debug("Eureka check failed: {}", e.getMessage());
                        return false;
                    }
                });

        log.info("✅ Eureka has discovered all services");
    }

    /**
     * Wait for a specific service to be healthy (direct hit, not via Gateway).
     */
    protected static void waitForServiceHealthy(String serviceName, String baseUri, String healthPath) {
        log.info("Waiting for {} to be healthy...", serviceName);

        await()
                .atMost(60, TimeUnit.SECONDS)
                .pollInterval(2000, TimeUnit.MILLISECONDS)
                .ignoreExceptions()
                .until(() -> {
                    try {
                        Response response = RestAssured.given()
                                .baseUri(baseUri)
                                .get(healthPath);
                        return response.getStatusCode() == 200;
                    } catch (Exception e) {
                        return false;
                    }
                });

        log.info("✅ {} is healthy", serviceName);
    }
}
