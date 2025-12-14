package com.learning.authservice.controller;

import com.learning.authservice.authorization.service.UserRoleService;
import com.learning.authservice.config.CognitoProperties;
import com.learning.authservice.dto.VerifyRequestDto;
import com.learning.authservice.signup.*;
import com.learning.common.dto.*;
import com.learning.common.infra.tenant.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Controller for handling user signup flows (B2C and B2B).
 * Thin controller that delegates to SignupService.
 */
@RestController
@RequestMapping("/api/v1/auth/signup")
@Slf4j
public class SignupController {

        private final SignupService signupService;
        private final CognitoIdentityProviderClient cognitoClient;
        private final CognitoProperties cognitoProperties;
        private final UserRoleService userRoleService;

        public SignupController(
                        SignupService signupService,
                        CognitoIdentityProviderClient cognitoClient,
                        CognitoProperties cognitoProperties,
                        UserRoleService userRoleService) {
                this.signupService = signupService;
                this.cognitoClient = cognitoClient;
                this.cognitoProperties = cognitoProperties;
                this.userRoleService = userRoleService;
        }

        /**
         * B2C Personal Signup Flow.
         * Creates personal tenant with email verification.
         */
        @PostMapping("/personal")
        public ResponseEntity<SignupResponse> signupPersonal(@RequestBody @Valid PersonalSignupRequest request) {
                log.info("B2C signup initiated: email={}", request.email());

                PersonalSignupData signupData = new PersonalSignupData(
                                request.email(),
                                request.password(),
                                request.name());

                SignupResponse response = signupService.signup(signupData);

                HttpStatus status = response.success() ? HttpStatus.CREATED : HttpStatus.BAD_REQUEST;
                return ResponseEntity.status(status).body(response);
        }

        /**
         * B2B Organization Signup Flow.
         * Creates organization tenant with email verification for admin.
         */
        @PostMapping("/organization")
        public ResponseEntity<SignupResponse> signupOrganization(
                        @RequestBody @Valid OrganizationSignupRequest request) {
                log.info("B2B signup initiated: company={} admin={}", request.companyName(), request.adminEmail());

                OrganizationSignupData signupData = new OrganizationSignupData(
                                request.adminEmail(),
                                request.password(),
                                request.adminName(),
                                request.companyName(),
                                request.tier(),
                                null // maxUsers - will use default
                );

                SignupResponse response = signupService.signup(signupData);

                HttpStatus status = response.success() ? HttpStatus.CREATED : HttpStatus.BAD_REQUEST;
                return ResponseEntity.status(status).body(response);
        }

        /**
         * Verify Email with Code.
         * Confirms user signup after receiving verification code via email.
         */
        @PostMapping("/verify")
        public ResponseEntity<SignupResponse> verifyEmail(@RequestBody @Valid VerifyRequestDto request) {
                log.info("Email verification initiated: email={}", request.getEmail());

                try {
                        String secretHash = calculateSecretHash(request.getEmail());
                        String role = request.getRole() != null ? request.getRole() : "tenant-admin";

                        // NOTE: tenantType is NOT stored in Cognito - frontend looks it up from
                        // platform DB
                        ConfirmSignUpRequest confirmRequest = ConfirmSignUpRequest.builder()
                                        .clientId(cognitoProperties.getClientId())
                                        .username(request.getEmail())
                                        .confirmationCode(request.getCode())
                                        .secretHash(secretHash)
                                        .clientMetadata(Map.of(
                                                        "tenantId", request.getTenantId(),
                                                        "role", role))
                                        .build();

                        cognitoClient.confirmSignUp(confirmRequest);

                        // After confirmation, get user's sub from Cognito and assign role in database
                        AdminGetUserRequest getUserRequest = AdminGetUserRequest.builder()
                                        .userPoolId(cognitoProperties.getUserPoolId())
                                        .username(request.getEmail())
                                        .build();

                        AdminGetUserResponse userResponse = cognitoClient.adminGetUser(getUserRequest);
                        String userId = userResponse.userAttributes().stream()
                                        .filter(attr -> "sub".equals(attr.name()))
                                        .map(AttributeType::value)
                                        .findFirst()
                                        .orElseThrow(() -> new RuntimeException("Failed to get user sub from Cognito"));

                        // Set tenant context for database routing
                        log.info("Setting TenantContext for role assignment: tenantId={}", request.getTenantId());
                        TenantContext.setCurrentTenant(request.getTenantId());
                        try {
                                // Assign role in database for authorization checks
                                log.info("Assigning role in tenant DB: userId={} role={}", userId, role);
                                userRoleService.assignRole(userId, role, "system");

                                log.info("✅ Email verified and role assigned: email={} userId={} role={} tenantId={}",
                                                request.getEmail(), userId, role, request.getTenantId());
                        } finally {
                                TenantContext.clear();
                        }

                        return ResponseEntity.ok(
                                        SignupResponse.success("Email verified successfully. You can now log in.", null,
                                                        true));

                } catch (ExpiredCodeException e) {
                        log.error("Verification code expired: email={}", request.getEmail());
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                        .body(SignupResponse.failure(
                                                        "Verification code has expired. Please request a new one."));
                } catch (CodeMismatchException e) {
                        log.error("Invalid verification code: email={}", request.getEmail());
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                        .body(SignupResponse.failure("Invalid verification code. Please try again."));
                } catch (CognitoIdentityProviderException e) {
                        log.error("Cognito verification error: email={} error={}", request.getEmail(), e.getMessage());
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                        .body(SignupResponse.failure(
                                                        "Verification failed: " + e.awsErrorDetails().errorMessage()));
                }
        }

        private String calculateSecretHash(String username) {
                try {
                        String message = username + cognitoProperties.getClientId();
                        Mac mac = Mac.getInstance("HmacSHA256");
                        SecretKeySpec secretKeySpec = new SecretKeySpec(
                                        cognitoProperties.getClientSecret().getBytes(StandardCharsets.UTF_8),
                                        "HmacSHA256");
                        mac.init(secretKeySpec);
                        byte[] rawHmac = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
                        return Base64.getEncoder().encodeToString(rawHmac);
                } catch (Exception e) {
                        throw new RuntimeException("Error calculating secret hash", e);
                }
        }
}
