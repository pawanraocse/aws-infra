package com.learning.authservice.controller;

import com.learning.authservice.signup.pipeline.SignupContext;
import com.learning.authservice.signup.pipeline.SignupContext.SignupType;
import com.learning.authservice.signup.pipeline.SignupPipeline;
import com.learning.authservice.signup.pipeline.SignupResult;
import com.learning.common.dto.SignupResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller for SSO signup completion.
 * 
 * Called after successful SSO authentication (Google, SAML, OIDC) to:
 * 1. Provision tenant (if personal/new)
 * 2. Create membership
 * 3. Assign roles in tenant DB
 * 
 * This endpoint replaces the JIT provisioning in platform-service,
 * ensuring auth-service is the single orchestrator for all signup flows.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class SsoCompletionController {

    private final SignupPipeline signupPipeline;

    /**
     * Complete SSO signup.
     * 
     * Called by Lambda or frontend after SSO authentication to:
     * - Create personal tenant (for Google social login)
     * - Create membership record
     * - Assign user roles in tenant DB
     */
    @PostMapping("/sso-complete")
    public ResponseEntity<SignupResponse> completeSsoSignup(
            @Valid @RequestBody SsoCompleteRequest request) {

        log.info("SSO completion request: email={}, tenantId={}, source={}",
                request.email(), request.tenantId(), request.source());

        // Determine signup type from source
        SignupType signupType = switch (request.source()) {
            case "GOOGLE", "google", "Google" -> SignupType.SSO_GOOGLE;
            case "SAML", "saml" -> SignupType.SSO_SAML;
            case "OIDC", "oidc" -> SignupType.SSO_OIDC;
            default -> SignupType.SSO_OIDC;
        };

        // Build context
        SignupContext ctx = SignupContext.builder()
                .email(request.email())
                .tenantId(request.tenantId())
                .cognitoUserId(request.cognitoUserId())
                .signupType(signupType)
                .ssoGroups(request.groups())
                .build();

        // Execute pipeline
        SignupResult result = signupPipeline.execute(ctx);

        if (result.success()) {
            return ResponseEntity.ok(SignupResponse.success(
                    result.message(),
                    result.tenantId(),
                    !result.requiresEmailVerification())); // SSO users are always confirmed
        } else {
            log.error("SSO completion failed: {}", result.message());
            return ResponseEntity.badRequest().body(
                    SignupResponse.failure(result.message()));
        }
    }

    /**
     * Request body for SSO completion.
     */
    public record SsoCompleteRequest(
            @NotBlank(message = "Tenant ID is required") String tenantId,

            @NotBlank(message = "Email is required") @Email(message = "Valid email required") String email,

            String cognitoUserId,

            @NotBlank(message = "Source is required") String source,

            String defaultRole,

            List<String> groups) {
    }
}
