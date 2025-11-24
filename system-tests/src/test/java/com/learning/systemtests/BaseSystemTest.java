package com.learning.systemtests;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for System Tests.
 * <p>
 * This configuration assumes the environment is ALREADY RUNNING via `docker-compose up`.
 * It connects to the Gateway on localhost:8080.
 * </p>
 */
public abstract class BaseSystemTest {

    protected static final Logger log = LoggerFactory.getLogger(BaseSystemTest.class);

    protected static final String GATEWAY_HOST = "localhost";
    protected static final int GATEWAY_PORT = 8080;
    protected static final int EUREKA_PORT = 8761;

    @BeforeAll
    static void setup() {
        // Configure RestAssured to point to the running Gateway
        RestAssured.baseURI = "http://" + GATEWAY_HOST;
        RestAssured.port = GATEWAY_PORT;
        
        log.info("System Tests Configured. Connecting to Gateway at {}:{}", GATEWAY_HOST, GATEWAY_PORT);
        log.info("Ensure you have run 'docker-compose up -d' before running these tests.");
    }
}
