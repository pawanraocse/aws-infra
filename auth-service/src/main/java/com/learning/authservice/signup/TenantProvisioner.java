package com.learning.authservice.signup;

import com.learning.common.dto.ProvisionTenantRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * Handles tenant provisioning via platform-service.
 * Uses pattern matching to build appropriate request for each signup type.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TenantProvisioner {

    private final WebClient platformWebClient;

    private static final Duration PROVISIONING_TIMEOUT = Duration.ofSeconds(60);

    /**
     * Provision a tenant for the given signup request.
     * 
     * @param request  the signup data
     * @param tenantId the generated tenant ID
     */
    public void provision(SignupRequest request, String tenantId) {
        log.info("Provisioning tenant: tenantId={} type={}", tenantId, request.tenantType());

        ProvisionTenantRequest tenantRequest = buildProvisionRequest(request, tenantId);

        platformWebClient.post()
                .uri("/internal/tenants")
                .bodyValue(tenantRequest)
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(PROVISIONING_TIMEOUT)
                .block();

        log.info("Tenant provisioned successfully: tenantId={}", tenantId);
    }

    private ProvisionTenantRequest buildProvisionRequest(SignupRequest request, String tenantId) {
        return switch (request) {
            case PersonalSignupData p ->
                ProvisionTenantRequest.forPersonal(tenantId, p.email());

            case OrganizationSignupData o ->
                ProvisionTenantRequest.forOrganization(
                        tenantId,
                        o.companyName(),
                        o.email(),
                        o.tier());
        };
    }
}
