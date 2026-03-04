package com.learning.authservice.signup.actions;

import com.learning.authservice.signup.pipeline.SignupAction;
import com.learning.authservice.signup.pipeline.SignupActionException;
import com.learning.authservice.signup.pipeline.SignupContext;
import com.learning.authservice.sqs.SqsProvisioningProducer;
import com.learning.common.dto.ProvisionTenantEvent;
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
 * For PERSONAL tenants: synchronous REST call (fast, shared schema).
 * For ORG tenants (when async enabled): creates tenant row + sends SQS message.
 * 
 * Prerequisites: TenantId must be generated (action 10)
 */
@Component
@Order(20)
@Slf4j
public class ProvisionTenantAction implements SignupAction {

    private final WebClient platformWebClient;
    private final SqsProvisioningProducer sqsProducer; // null when async disabled
    private final boolean asyncEnabled;

    public ProvisionTenantAction(
            WebClient.Builder webClientBuilder,
            @Value("${services.platform-service.url:http://platform-service:8083}") String platformServiceUrl,
            @Value("${app.async-provision.enabled:false}") boolean asyncEnabled,
            @org.springframework.beans.factory.annotation.Autowired(required = false) SqsProvisioningProducer sqsProducer) {
        this.platformWebClient = webClientBuilder.baseUrl(platformServiceUrl).build();
        this.asyncEnabled = asyncEnabled;
        this.sqsProducer = sqsProducer;
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
            log.info("Provisioning tenant: {} for type: {}", ctx.getTenantId(), ctx.getSignupType());

            // Build provision request using shared DTO factory methods
            ProvisionTenantRequest request = buildProvisionRequest(ctx);

            log.debug("Provisioning request: tenantId={}, type={}, owner={}",
                    request.id(), request.tenantType(), request.ownerEmail());

            if (asyncEnabled && ctx.isOrganizationSignup() && sqsProducer != null) {
                // ASYNC path for ORG tenants:
                // 1. Create tenant row with PROVISIONING status (lightweight REST call)
                // 2. Send SQS message for heavy provisioning (DB creation, migrations)
                executeAsync(ctx, request);
            } else {
                // SYNC path for PERSONAL tenants (or when async disabled):
                // Full provisioning in a single blocking REST call
                executeSync(ctx, request);
            }
        } catch (SignupActionException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to provision tenant {}: {}", ctx.getTenantId(), e.getMessage(), e);
            throw new SignupActionException(getName(),
                    "Failed to provision tenant: " + e.getMessage(), e);
        }
    }

    /**
     * Synchronous provisioning (PERSONAL tenants or async disabled).
     * Calls platform-service REST endpoint which blocks until DB + migrations complete.
     */
    private void executeSync(SignupContext ctx, ProvisionTenantRequest request) throws SignupActionException {
        // For personal signup: check if user already has a personal tenant
        if (!ctx.isOrganizationSignup()) {
            Boolean canCreate = platformWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/platform/internal/memberships/can-create-personal")
                            .queryParam("email", ctx.getEmail())
                            .build())
                    .retrieve()
                    .bodyToMono(java.util.Map.class)
                    .map(map -> (Boolean) map.get("canCreate"))
                    .block();

            if (Boolean.FALSE.equals(canCreate)) {
                throw new SignupActionException(getName(),
                        "User already has a personal workspace. Only one personal workspace per email is allowed.");
            }
        }

        // Call platform-service to provision (blocking)
        platformWebClient.post()
                .uri("/platform/internal/tenants")
                .bodyValue(request)
                .retrieve()
                .toBodilessEntity()
                .block();

        log.info("Tenant provisioned synchronously: {}", ctx.getTenantId());
    }

    /**
     * Async provisioning (ORG tenants).
     * Creates tenant row via REST, then sends SQS message for heavy work.
     */
    private void executeAsync(SignupContext ctx, ProvisionTenantRequest request) {
        // Step 1: Create tenant row with PROVISIONING status (fast REST call)
        platformWebClient.post()
                .uri("/platform/internal/tenants/init")
                .bodyValue(request)
                .retrieve()
                .toBodilessEntity()
                .block();

        // Step 2: Send SQS message for actual provisioning (DB creation, migrations)
        ProvisionTenantEvent event = ProvisionTenantEvent.fromRequest(request);
        sqsProducer.sendProvisionEvent(event);

        log.info("Tenant provisioning initiated asynchronously: {}", ctx.getTenantId());
    }

    private ProvisionTenantRequest buildProvisionRequest(SignupContext ctx) throws SignupActionException {
        if (ctx.isOrganizationSignup()) {
            String tier = ctx.getTier() != null ? ctx.getTier() : "STANDARD";
            return ProvisionTenantRequest.forOrganization(
                    ctx.getTenantId(),
                    ctx.getCompanyName(),
                    ctx.getEmail(),
                    tier);
        } else {
            return ProvisionTenantRequest.forPersonal(
                    ctx.getTenantId(),
                    ctx.getEmail());
        }
    }

    @Override
    public void rollback(SignupContext ctx) {
        // Tenant deletion is complex - log for manual cleanup
        log.warn("Rollback requested for tenant {}: manual cleanup may be needed",
                ctx.getTenantId());
    }
}
