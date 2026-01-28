package com.learning.authservice.config;

import com.learning.authservice.authorization.service.GroupRoleMappingService;
import com.learning.authservice.authorization.service.LocalRoleLookupService;
import com.learning.authservice.authorization.service.UserRoleService;
import com.learning.common.infra.security.RoleLookupService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class RoleLookupConfig {

    @Bean
    @Primary
    public RoleLookupService localRoleLookupService(
            UserRoleService userRoleService,
            GroupRoleMappingService groupRoleMappingService) {
        return new LocalRoleLookupService(userRoleService, groupRoleMappingService);
    }
}
