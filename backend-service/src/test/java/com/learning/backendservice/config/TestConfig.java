package com.learning.backendservice.config;

import com.learning.common.infra.security.PermissionEvaluator;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import javax.sql.DataSource;
import java.util.Properties;

@TestConfiguration
public class TestConfig {

    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("com.learning.backendservice.entity");

        JpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);

        Properties properties = new Properties();
        properties.setProperty("hibernate.hbm2ddl.auto", "create-drop");
        properties.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        properties.setProperty("hibernate.show_sql", "true");
        properties.setProperty("hibernate.format_sql", "true");
        em.setJpaProperties(properties);

        return em;
    }

    /**
     * Mock PermissionEvaluator for tests.
     * Always grants permissions to avoid needing auth-service running.
     */
    @Bean
    @Primary
    public PermissionEvaluator permissionEvaluator() {
        return (userId, resource, action) -> true;
    }

    /**
     * In-memory CacheManager for tests.
     * Provides the 'permissions' cache needed by @Cacheable on
     * RemotePermissionEvaluator.
     */
    @Bean
    @Primary
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("permissions");
    }

    /**
     * Mock RedissonClient for tests.
     * Needed by TenantContextFilter.
     */
    @Bean
    @Primary
    public org.redisson.api.RedissonClient redissonClient() {
        return org.mockito.Mockito.mock(org.redisson.api.RedissonClient.class, org.mockito.Mockito.RETURNS_MOCKS);
    }
}
