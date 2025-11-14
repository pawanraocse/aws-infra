package com.learning.backendservice.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

@TestConfiguration
@EnableJpaAuditing
public class TestAuditConfig {
    @Bean
    public AuditorAware<String> testAuditorProvider() {
        // Matches expectation in tests for createdBy/updatedBy
        return () -> Optional.of("test-user");
    }
}

