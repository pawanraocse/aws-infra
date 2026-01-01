package com.learning.authservice.signup.actions;

import com.learning.authservice.signup.pipeline.SignupAction;
import com.learning.authservice.signup.pipeline.SignupActionException;
import com.learning.authservice.signup.pipeline.SignupContext;
import com.learning.common.dto.ProvisionTenantRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Action to provision a new tenant in platform-service.
 * 
 * Order: 20
 * 
 * This creates the tenant record and database in platform-service.
 * Prerequisites: TenantId must be generated (action 10)
 */
@Component
@Order(20)
@Slf4j
public class ProvisionTenantAction implements SignupAction {

    private final WebClient platformWebClient;

    public ProvisionTenantAction(
            WebClient.Builder webClientBuilder,
            @Value("${services.platform-service.url:http://platform-service:8083}") String platformServiceUrl) {
        this.platformWebClient = webClientBuilder.baseUrl(platformServiceUrl).build();
    }

    @Override
    public String getName() {
        return "ProvisionTenant";
    }

    @Override
    public int getOrder() {
        return 20;
    }

    @Override
    public boolean supports(SignupContext ctx) {
        // All signup types need tenant provisioning
        return true;
    }

    @Override
    public boolean isAlreadyDone(SignupContext ctx) {
        // Check if tenant already exists in platform-service
        if (ctx.getTenantId() == null) {
            return false;
        }

        try {
            Boolean exists = platformWebClient.get()
                    .uri("/platform/internal/tenants/{tenantId}/exists", ctx.getTenantId())
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .block();

            if (Boolean.TRUE.equals(exists)) {
                log.debug("Tenant {} already exists, skipping provisioning", ctx.getTenantId());
                return true;
            }
            return false;
        } catch (Exception e) {
            log.warn("Failed to check tenant existence: {} - will try to provision",
                    ctx.getTenantId(), e);
            return false;
        }
    }

    @Override
    public void execute(SignupContext ctx) throws SignupActionException {
        try {
            log.info("Provisioning tenant: {} for type: {}",
                    ctx.getTenantId(), ctx.getSignupType());

            // Build provision request using shared DTO factory methods
            ProvisionTenantRequest request;
            if (ctx.isOrganizationSignup()) {
                String tier = ctx.getTier() != null ? ctx.getTier() : "STANDARD";
                request = ProvisionTenantRequest.forOrganization(
                        ctx.getTenantId(),
                        ctx.getCompanyName(),
                        ctx.getEmail(),
                        tier);
            } else {
                request = ProvisionTenantRequest.forPersonal(
                        ctx.getTenantId(),
                        ctx.getEmail());
            }

            log.debug("Provisioning request: tenantId={}, type={}, owner={}",
                    request.id(), request.tenantType(), request.ownerEmail());

            // Call platform-service to provision
            platformWebClient.post()
                    .uri("/platform/internal/tenants")
                    .bodyValue(request)
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            log.info("Tenant provisioned successfully: {}", ctx.getTenantId());

        } catch (Exception e) {
            log.error("Failed to provision tenant {}: {}", ctx.getTenantId(), e.getMessage(), e);
            throw new SignupActionException(getName(),
                    "Failed to provision tenant: " + e.getMessage(), e);
        }
    }

    @Override
    public void rollback(SignupContext ctx) {
        // Tenant deletion is complex - log for manual cleanup
        log.warn("Rollback requested for tenant {}: manual cleanup may be needed",
                ctx.getTenantId());
    }
}
