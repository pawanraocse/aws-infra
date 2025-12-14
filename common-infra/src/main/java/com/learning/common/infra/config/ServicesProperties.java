package com.learning.common.infra.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Shared service URL configuration for inter-service communication.
 * Each service that needs to call other services uses this to get base URLs.
 * 
 * Default values are set for standard Eureka service discovery.
 * Override in application.yml only if needed:
 * 
 * <pre>
 * services:
 *   platform:
 *     base-url: http://custom-host/platform
 * </pre>
 */
@ConfigurationProperties(prefix = "services")
@Getter
@Setter
public class ServicesProperties {
    private ServiceRef platform = new ServiceRef("http://platform-service/platform");
    private ServiceRef backend = new ServiceRef("http://backend-service/api");
    private ServiceRef auth = new ServiceRef("http://auth-service/auth");
    private ServiceRef file = new ServiceRef("http://file-service/file");

    @Getter
    @Setter
    public static class ServiceRef {
        private String baseUrl;

        public ServiceRef() {
        }

        public ServiceRef(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }
}
