package com.learning.authservice.signup.actions;

import com.learning.authservice.signup.TenantIdGenerator;
import com.learning.authservice.signup.pipeline.SignupAction;
import com.learning.authservice.signup.pipeline.SignupActionException;
import com.learning.authservice.signup.pipeline.SignupContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Action to generate a unique tenant ID.
 * 
 * Order: 10 (first action)
 * 
 * For SSO signups, tenantId may already be set in context (from JWT).
 * For normal signups, generates based on email/company name.
 */
@Component
@Order(10)
@Slf4j
@RequiredArgsConstructor
public class GenerateTenantIdAction implements SignupAction {

    private final TenantIdGenerator tenantIdGenerator;

    @Override
    public String getName() {
        return "GenerateTenantId";
    }

    @Override
    public int getOrder() {
        return 10;
    }

    @Override
    public boolean supports(SignupContext ctx) {
        // All signup types need a tenant ID
        return true;
    }

    @Override
    public boolean isAlreadyDone(SignupContext ctx) {
        // Already done if tenantId is set (e.g., SSO provides it)
        return ctx.getTenantId() != null && !ctx.getTenantId().isBlank();
    }

    @Override
    public void execute(SignupContext ctx) throws SignupActionException {
        try {
            String tenantId;

            switch (ctx.getSignupType()) {
                case PERSONAL, SSO_GOOGLE -> {
                    // Personal tenant: personal-{username}
                    tenantId = tenantIdGenerator.generatePersonalTenantId(ctx.getEmail());
                }
                case ORGANIZATION -> {
                    // Organization tenant: sanitized company name
                    tenantId = tenantIdGenerator.generateOrganizationTenantId(ctx.getCompanyName());
                }
                case SSO_SAML, SSO_OIDC -> {
                    // SSO org: should already have tenantId from IdP
                    throw new SignupActionException(getName(),
                            "SSO organization signup requires tenantId from IdP");
                }
                default -> throw new SignupActionException(getName(),
                        "Unknown signup type: " + ctx.getSignupType());
            }

            ctx.setTenantId(tenantId);
            log.info("Generated tenant ID: {} for type: {}", tenantId, ctx.getSignupType());

        } catch (SignupActionException e) {
            throw e;
        } catch (Exception e) {
            throw new SignupActionException(getName(),
                    "Failed to generate tenant ID: " + e.getMessage(), e);
        }
    }

    @Override
    public void rollback(SignupContext ctx) {
        // Nothing to rollback - just clearing the ID
        ctx.setTenantId(null);
    }
}
