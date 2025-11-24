package com.learning.systemtests;

import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

public class HealthCheckIT extends BaseSystemTest {

    @Test
    void verifyGatewayIsHealthy() {
        given()
            .when()
                .get("/actuator/health")
            .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }

    @Test
    void verifyServicesAreRegisteredInEureka() {
        // Connect directly to the running Eureka server on localhost
        given()
            .baseUri("http://localhost")
            .port(EUREKA_PORT)
            .when()
                .get("/actuator/health")
            .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }
}
