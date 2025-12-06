package com.learning.authservice.config;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for integration tests with Testcontainers.
 * Provides shared PostgreSQL container that starts once and is reused across
 * all tests.
 * This significantly improves test execution speed.
 * 
 * Usage: Extend this class for any integration test that needs database access.
 */
@SpringBootTest(properties = {
        "COGNITO_CLIENT_ID=test-client-id",
        "COGNITO_CLIENT_SECRET=test-client-secret",
        "COGNITO_ISSUER_URI=https://cognito-idp.us-east-1.amazonaws.com/us-east-1_test",
        "COGNITO_USER_POOL_ID=us-east-1_test",
        "COGNITO_DOMAIN=test-domain",
        "spring.main.allow-bean-definition-overriding=true"
})
@Testcontainers
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    /**
     * Shared PostgreSQL container (starts once for all tests in the test suite).
     * Using static container significantly improves test performance by reusing
     * the same database instance across multiple test classes.
     */
    protected static final PostgreSQLContainer<?> postgres;

    static {
        postgres = new PostgreSQLContainer<>("postgres:15")
                .withDatabaseName("testdb")
                .withUsername("test")
                .withPassword("test")
                .withReuse(true); // Enable container reuse

        postgres.start();
    }

    /**
     * Configure Spring properties to point to Testcontainers PostgreSQL.
     * This runs early enough that Spring context can use these values during
     * initialization.
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Platform datasource configuration
        registry.add("app.datasource.platform.url", postgres::getJdbcUrl);
        registry.add("app.datasource.platform.username", postgres::getUsername);
        registry.add("app.datasource.platform.password", postgres::getPassword);

        // Disable Flyway auto-migration in tests (schemas are created by Hibernate)
        registry.add("app.flyway.platform.enabled", () -> "false");
        registry.add("spring.flyway.enabled", () -> "false");

        // JPA configuration for tests
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.show-sql", () -> "false");
    }
}
