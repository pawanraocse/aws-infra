package com.learning.platformservice.test;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

@TestConfiguration
public class TestWebClientOverrideConfig {

        @Bean(name = "internalWebClientBuilder")
        @Primary
        public WebClient.Builder internalWebClientBuilderTestOverride() {
                System.out.println(">>> TEST WebClient.Builder OVERRIDE LOADED <<<");

                return WebClient.builder()
                                .clientConnector(new ReactorClientHttpConnector());
        }

        /**
         * Mock RedissonClient for tests.
         * Needed by TenantContextFilter in common-infra.
         */
        @Bean
        @Primary
        public org.redisson.api.RedissonClient redissonClient() {
                return org.mockito.Mockito.mock(org.redisson.api.RedissonClient.class,
                                org.mockito.Mockito.RETURNS_MOCKS);
        }

        @Bean
        @Primary
        public org.springframework.cache.CacheManager cacheManager() {
                org.springframework.cache.concurrent.ConcurrentMapCacheManager cacheManager = new org.springframework.cache.concurrent.ConcurrentMapCacheManager();
                cacheManager.setCacheNames(com.learning.common.infra.cache.CacheNames.all());
                return cacheManager;
        }

}
