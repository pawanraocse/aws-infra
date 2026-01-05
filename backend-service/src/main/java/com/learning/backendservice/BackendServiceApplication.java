package com.learning.backendservice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

@Slf4j
@EnableDiscoveryClient
@SpringBootApplication
@ComponentScan(basePackages = {
        "com.learning.backendservice",
        "com.learning.common.infra.tenant",
        "com.learning.common.infra.security",
        "com.learning.common.infra.openfga"
})
public class BackendServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendServiceApplication.class, args);
        log.info("Backend Service started successfully");
    }
}
