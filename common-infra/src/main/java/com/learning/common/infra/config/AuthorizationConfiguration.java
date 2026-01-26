package com.learning.common.infra.config;

import com.learning.common.infra.security.PermissionEvaluator;
import com.learning.common.infra.security.RemotePermissionEvaluator;
import com.learning.common.infra.security.RoleLookupService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuration for authorization beans.
 * Defines the PermissionEvaluator used by AuthorizationAspect.
 */
@Configuration
public class AuthorizationConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "authWebClient")
    public WebClient authWebClient(@Qualifier("internalWebClientBuilder") WebClient.Builder webClientBuilder) {
        return webClientBuilder.build();
    }

    @Bean
    @ConditionalOnMissingBean(PermissionEvaluator.class)
    public PermissionEvaluator permissionEvaluator(
            @Qualifier("authWebClient") WebClient authWebClient,
            RoleLookupService roleLookupService) {
        return new RemotePermissionEvaluator(authWebClient, roleLookupService);
    }
}
