package com.learning.authservice.signup.actions;

import com.learning.authservice.signup.pipeline.SignupAction;
import com.learning.authservice.signup.pipeline.SignupActionException;
import com.learning.authservice.signup.pipeline.SignupContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Action to create organization settings.
 * 
 * Order: 70
 * 
 * Only runs for organization signups.
 * Creates default org profile, billing settings, etc.
 */
@Component
@Order(70)
@Slf4j
@RequiredArgsConstructor
public class CreateOrgSettingsAction implements SignupAction {

    private final WebClient platformWebClient;

    @Override
    public String getName() {
        return "CreateOrgSettings";
    }

    @Override
    public int getOrder() {
        return 70;
    }

    @Override
    public boolean supports(SignupContext ctx) {
        // Only for organization signups
        return ctx.isOrganizationSignup();
    }

    @Override
    public boolean isAlreadyDone(SignupContext ctx) {
        // Check if org settings already exist
        try {
            platformWebClient.get()
                    .uri("/internal/organizations/{tenantId}", ctx.getTenantId())
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            log.debug("Organization settings already exist for: {}", ctx.getTenantId());
            return true;
        } catch (WebClientResponseException.NotFound e) {
            return false;
        } catch (Exception e) {
            log.warn("Failed to check org settings: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void execute(SignupContext ctx) throws SignupActionException {
        try {
            log.info("Creating organization settings for: {}", ctx.getTenantId());

            var request = new OrgSettingsRequest(
                    ctx.getTenantId(),
                    ctx.getCompanyName(),
                    ctx.getTier() != null ? ctx.getTier() : "STANDARD");

            platformWebClient.post()
                    .uri("/internal/organizations")
                    .bodyValue(request)
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            log.info("Organization settings created successfully");

        } catch (Exception e) {
            throw new SignupActionException(getName(),
                    "Failed to create org settings: " + e.getMessage(), e);
        }
    }

    private record OrgSettingsRequest(
            String tenantId,
            String companyName,
            String tier) {
    }
}
