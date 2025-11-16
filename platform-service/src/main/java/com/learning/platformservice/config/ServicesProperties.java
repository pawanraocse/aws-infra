package com.learning.platformservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "services")
@Getter
@Setter
public class ServicesProperties {
    private ServiceRef platform = new ServiceRef();
    private ServiceRef backend = new ServiceRef();
    private ServiceRef auth = new ServiceRef();
    private ServiceRef file = new ServiceRef();

    @Getter
    @Setter
    public static class ServiceRef {
        private String baseUrl;
    }
}
