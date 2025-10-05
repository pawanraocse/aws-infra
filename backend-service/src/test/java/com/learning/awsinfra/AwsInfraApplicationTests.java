package com.learning.awsinfra;

import com.learning.awsinfra.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

class AwsInfraApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
        // Spring Boot will start the application context
        // using the Testcontainers PostgreSQL automatically
    }
}
