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
 * Action to create user-tenant membership in platform DB.
 * 
 * Order: 40
 * 
 * Creates the membership record linking user to tenant.
 * Called via platform-service internal API.
 */
@Component
@Order(40)
@Slf4j
@RequiredArgsConstructor
public class CreateMembershipAction implements SignupAction {

    private final WebClient platformWebClient;

    @Override
    public String getName() {
        return "CreateMembership";
    }

    @Override
    public int getOrder() {
        return 40;
    }

    @Override
    public boolean supports(SignupContext ctx) {
        // All signup types need membership
        return true;
    }

    @Override
    public boolean isAlreadyDone(SignupContext ctx) {
        // Check if membership already exists
        try {
            var response = platformWebClient.get()
                    .uri("/internal/memberships/exists?tenantId={tenantId}&email={email}",
                            ctx.getTenantId(), ctx.getEmail())
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .block();

            if (Boolean.TRUE.equals(response)) {
                log.debug("Membership already exists: {} in {}", ctx.getEmail(), ctx.getTenantId());
                return true;
            }
            return false;
        } catch (WebClientResponseException.NotFound e) {
            return false;
        } catch (Exception e) {
            log.warn("Failed to check membership existence: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void execute(SignupContext ctx) throws SignupActionException {
        try {
            log.info("Creating membership: {} in tenant {}", ctx.getEmail(), ctx.getTenantId());

            var request = new MembershipRequest(
                    ctx.getTenantId(),
                    ctx.getEmail(),
                    ctx.getCognitoUserId(),
                    ctx.getAssignedRole() != null ? ctx.getAssignedRole() : "admin",
                    true, // isOwner for first user
                    true // isDefault
            );

            platformWebClient.post()
                    .uri("/internal/memberships")
                    .bodyValue(request)
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            log.info("Membership created successfully");

        } catch (Exception e) {
            throw new SignupActionException(getName(),
                    "Failed to create membership: " + e.getMessage(), e);
        }
    }

    @Override
    public void rollback(SignupContext ctx) {
        // Could delete membership, but safer to log for manual cleanup
        log.warn("Rollback requested for membership {}/{}: manual cleanup may be needed",
                ctx.getTenantId(), ctx.getEmail());
    }

    private record MembershipRequest(
            String tenantId,
            String email,
            String cognitoUserId,
            String roleHint,
            boolean isOwner,
            boolean isDefault) {
    }
}
