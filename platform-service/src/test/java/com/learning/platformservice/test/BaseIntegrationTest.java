package com.learning.platformservice.test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.learning.platformservice.PlatformServiceApplication;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@SpringBootTest(classes = {
                PlatformServiceApplication.class,
                TestWebClientOverrideConfig.class
})
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
public abstract class BaseIntegrationTest {

        private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:16.4-alpine");

        @Container
        protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE)
                        .withDatabaseName("platform_it")
                        .withUsername("platform")
                        .withPassword("platform")
                        .withReuse(true)
                        .waitingFor(
                                        Wait.forListeningPort()
                                                        .withStartupTimeout(Duration.ofSeconds(30)));

        // -------------------------------
        // WireMock: runs once per test suite
        // -------------------------------
        protected static final WireMockServer wireMockServer = new WireMockServer(
                        WireMockConfiguration.options().dynamicPort());

        static {
                wireMockServer.start();

                configureFor("localhost", wireMockServer.port());

                // Stub for Auth Service migration endpoint
                wireMockServer.stubFor(
                                post(urlPathMatching("/auth/internal/tenants/.*/migrate"))
                                                .willReturn(aResponse()
                                                                .withStatus(200)
                                                                .withHeader("Content-Type", "application/json")
                                                                .withBody("{\"lastVersion\":\"1.0.0\"}")));

                // Stub for Backend Service migration endpoint
                wireMockServer.stubFor(
                                post(urlPathMatching("/internal/tenants/.*/migrate"))
                                                .willReturn(aResponse()
                                                                .withStatus(200)
                                                                .withHeader("Content-Type", "application/json")
                                                                .withBody("{\"lastVersion\":\"1.0.0\"}")));

                // Stub for Auth Service permission check
                wireMockServer.stubFor(
                                post(urlPathMatching("/api/v1/permissions/check"))
                                                .willReturn(aResponse()
                                                                .withStatus(200)
                                                                .withHeader("Content-Type", "application/json")
                                                                .withBody("true")));

                // Stub for Payment Service migration endpoint
                wireMockServer.stubFor(
                                post(urlPathMatching("/payment-service/api/v1/payment/internal/migrate"))
                                                .willReturn(aResponse()
                                                                .withStatus(200)
                                                                .withHeader("Content-Type", "application/json")
                                                                .withBody("{\"lastVersion\":\"1.0.0\"}")));
        }

        @DynamicPropertySource
        static void configureDataSource(DynamicPropertyRegistry registry) {
                registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
                registry.add("spring.datasource.username", POSTGRES::getUsername);
                registry.add("spring.datasource.password", POSTGRES::getPassword);
                registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);

                registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
                registry.add("spring.jpa.open-in-view", () -> "false");

                registry.add("spring.datasource.hikari.maximum-pool-size", () -> "5");
                registry.add("spring.datasource.hikari.minimum-idle", () -> "1");
                registry.add("services.backend.base-url",
                                () -> "http://localhost:" + wireMockServer.port());
                registry.add("services.auth.base-url",
                                () -> "http://localhost:" + wireMockServer.port());
                registry.add("services.payment.base-url",
                                () -> "http://localhost:" + wireMockServer.port());
        }

}
