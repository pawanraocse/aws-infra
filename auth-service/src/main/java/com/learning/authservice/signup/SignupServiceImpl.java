package com.learning.authservice.signup;

import com.learning.authservice.signup.pipeline.SignupContext;
import com.learning.authservice.signup.pipeline.SignupContext.SignupType;
import com.learning.authservice.signup.pipeline.SignupPipeline;
import com.learning.authservice.signup.pipeline.SignupResult;
import com.learning.common.dto.SignupResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementation of SignupService using the unified SignupPipeline.
 * 
 * Delegates all signup orchestration to SignupPipeline which:
 * - Executes actions in order (GenerateTenantId → Provision → Cognito →
 * Membership → Roles → Email)
 * - Handles idempotency (skips already-done actions on retry)
 * - Supports rollback on failure
 * 
 * SOLID Principles:
 * - Single Responsibility: Translates request types to pipeline context
 * - Open/Closed: New actions can be added without modifying this class
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SignupServiceImpl implements SignupService {

    private final SignupPipeline signupPipeline;

    @Override
    public SignupResponse signup(SignupRequest request) {
        log.info("Processing {} signup for: {}", request.tenantType(), request.email());

        try {
            // Convert request to pipeline context
            SignupContext ctx = toSignupContext(request);

            // Execute the unified pipeline
            SignupResult result = signupPipeline.execute(ctx);

            if (result.success()) {
                String message = result.requiresEmailVerification()
                        ? "Signup complete. Please verify your email."
                        : "New workspace created! Please login to access it.";

                return SignupResponse.success(
                        message,
                        result.tenantId(),
                        !result.requiresEmailVerification());
            } else {
                return SignupResponse.failure(result.message());
            }

        } catch (IllegalArgumentException e) {
            log.warn("Signup validation failed: {}", e.getMessage());
            return SignupResponse.failure(e.getMessage());
        } catch (Exception e) {
            log.error("Signup failed: email={} error={}", request.email(), e.getMessage(), e);
            return SignupResponse.failure(e.getMessage());
        }
    }

    /**
     * Convert SignupRequest to SignupContext for pipeline processing.
     */
    private SignupContext toSignupContext(SignupRequest request) {
        return switch (request) {
            case PersonalSignupData p -> SignupContext.builder()
                    .email(p.email())
                    .password(p.password())
                    .name(p.name())
                    .signupType(SignupType.PERSONAL)
                    .build();

            case OrganizationSignupData o -> SignupContext.builder()
                    .email(o.email())
                    .password(o.password())
                    .name(o.name())
                    .companyName(o.companyName())
                    .tier(o.tier())
                    .signupType(SignupType.ORGANIZATION)
                    .build();
        };
    }
}
