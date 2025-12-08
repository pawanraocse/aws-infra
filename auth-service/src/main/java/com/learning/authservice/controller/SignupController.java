package com.learning.authservice.controller;

import com.learning.authservice.authorization.service.UserRoleService;
import com.learning.authservice.config.CognitoProperties;
import com.learning.authservice.dto.VerifyRequestDto;
import com.learning.common.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Controller for handling user signup flows (B2C and B2B).
 * Orchestrates tenant provisioning and Cognito user creation.
 */
@RestController
@RequestMapping("/api/v1/auth/signup")
@Slf4j
@RequiredArgsConstructor
public class SignupController {

        private final CognitoIdentityProviderClient cognitoClient;
        private final CognitoProperties cognitoProperties;
        private final WebClient platformWebClient;
        private final UserRoleService userRoleService;

        /**
         * B2C Personal Signup Flow
         * 1. Generate tenant ID
         * 2. Provision tenant via platform-service
         * 3. Create Cognito user with custom:tenantId
         * 4. Return success
         */
        @PostMapping("/personal")
        public ResponseEntity<SignupResponse> signupPersonal(@RequestBody @Valid PersonalSignupRequest request) {
                String email = request.email();
                log.info("B2C signup initiated: email={}", email);

                try {
                        // 1. Generate unique tenant ID
                        String tenantId = generateTenantId(email);

                        // 2. Provision tenant
                        ProvisionTenantRequest tenantRequest = ProvisionTenantRequest.forPersonal(tenantId, email);
                        provisionTenant(tenantRequest);

                        // 3. Create Cognito user with tenant context
                        // Use signUp API to valid email verification
                        boolean userConfirmed = registerUserViaSignUp(
                                        email,
                                        request.password(),
                                        request.name(),
                                        tenantId,
                                        "tenant-admin" // Personal user is admin of their own tenant
                        );

                        log.info("✅ B2C signup completed: email={} tenantId={}", email, tenantId);

                        return ResponseEntity.status(HttpStatus.CREATED)
                                        .body(SignupResponse.success(
                                                        userConfirmed ? "Signup successful. Please log in."
                                                                        : "Signup successful. Please verify your email.",
                                                        tenantId,
                                                        userConfirmed));

                } catch (Exception e) {
                        log.error("❌ B2C signup failed: email={} error={}", email, e.getMessage(), e);
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                        .body(SignupResponse.failure(e.getMessage()));
                }
        }

        /**
         * Verify Email with Code
         * Confirms user signup after receiving verification code via email
         */
        @PostMapping("/verify")
        public ResponseEntity<SignupResponse> verifyEmail(@RequestBody @Valid VerifyRequestDto request) {
                log.info("Email verification initiated: email={}", request.getEmail());

                try {
                        String secretHash = calculateSecretHash(request.getEmail());
                        String role = request.getRole() != null ? request.getRole() : "tenant-admin";

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

                        // Assign role in database for authorization checks
                        userRoleService.assignRole(userId, role, "system");

                        log.info("✅ Email verified and role assigned: email={} userId={} role={}",
                                        request.getEmail(), userId, role);

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

        /**
         * B2B Organization Signup Flow
         * 1. Validate company domain
         * 2. Generate tenant ID from company name
         * 3. Provision organization tenant
         * 4. Create admin Cognito user
         * 5. Send admin notification
         */
        @PostMapping("/organization")
        public ResponseEntity<SignupResponse> signupOrganization(
                        @RequestBody @Valid OrganizationSignupRequest request) {
                String adminEmail = request.adminEmail();
                String companyName = request.companyName();

                log.info("B2B signup initiated: company={} admin={}", companyName, adminEmail);

                try {
                        // 1. Validate corporate email (optional)
                        // TODO: Re-enable for SSO/Enterprise tier - validateCorporateEmail(adminEmail);

                        // 2. Generate tenant ID from company name
                        String tenantId = slugify(companyName);

                        // 3. Provision organization tenant
                        ProvisionTenantRequest tenantRequest = ProvisionTenantRequest.forOrganization(
                                        tenantId,
                                        companyName,
                                        adminEmail,
                                        request.tier() != null ? request.tier() : "STANDARD");
                        provisionTenant(tenantRequest);

                        // 4. Create admin user
                        createCognitoUser(
                                        adminEmail,
                                        request.password(),
                                        request.adminName(),
                                        tenantId,
                                        TenantType.ORGANIZATION,
                                        "tenant-admin" // Admin role
                        );

                        log.info("✅ B2B signup completed: company={} tenantId={} admin={}",
                                        companyName, tenantId, adminEmail);

                        return ResponseEntity.status(HttpStatus.CREATED)
                                        .body(SignupResponse.success(
                                                        "Organization created successfully. Admin user has been notified.",
                                                        tenantId,
                                                        true)); // Admin created via adminCreateUser is auto-verified

                } catch (Exception e) {
                        log.error("❌ B2B signup failed: company={} error={}", companyName, e.getMessage(), e);
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                        .body(SignupResponse.failure(e.getMessage()));
                }
        }

        // Helper: Call platform-service to provision tenant
        private void provisionTenant(ProvisionTenantRequest request) {
                platformWebClient.post()
                                .uri("/platform/internal/tenants")
                                .bodyValue(request)
                                .retrieve()
                                .bodyToMono(Void.class)
                                .timeout(Duration.ofSeconds(60)) // Tenant provisioning can take time
                                .block();

                log.info("Tenant provisioned: tenantId={}", request.id());
        }

        // Helper: Register user via signUp (B2C) - Enforces email verification
        private boolean registerUserViaSignUp(
                        String email,
                        String password,
                        String name,
                        String tenantId,
                        String role) {
                try {
                        String secretHash = calculateSecretHash(email);

                        SignUpRequest signUpRequest = SignUpRequest.builder()
                                        .clientId(cognitoProperties.getClientId())
                                        .username(email)
                                        .password(password)
                                        .secretHash(secretHash)
                                        .userAttributes(
                                                        AttributeType.builder().name("email").value(email).build(),
                                                        AttributeType.builder().name("name").value(name).build())
                                        .clientMetadata(Map.of(
                                                        "tenantId", tenantId,
                                                        "role", role))
                                        .build();

                        SignUpResponse response = cognitoClient.signUp(signUpRequest);

                        log.info("Cognito user registered via signUp: email={} tenantId={} role={} confirmed={}",
                                        email, tenantId, role, response.userConfirmed());

                        return response.userConfirmed();

                } catch (UsernameExistsException e) {
                        throw new IllegalArgumentException("User with email " + email + " already exists");
                } catch (CognitoIdentityProviderException e) {
                        log.error("Cognito error: {}", e.awsErrorDetails().errorMessage());
                        throw new RuntimeException("Failed to register user: " + e.awsErrorDetails().errorMessage());
                }
        }

        // Helper: Create Cognito user with custom attributes
        private void createCognitoUser(
                        String email,
                        String password,
                        String name,
                        String tenantId,
                        TenantType tenantType,
                        String role) {
                try {
                        // Create user
                        AdminCreateUserRequest createRequest = AdminCreateUserRequest.builder()
                                        .userPoolId(cognitoProperties.getUserPoolId())
                                        .username(email)
                                        .userAttributes(
                                                        AttributeType.builder().name("email").value(email).build(),
                                                        AttributeType.builder().name("name").value(name).build(),
                                                        AttributeType.builder().name("email_verified").value("true")
                                                                        .build(),
                                                        AttributeType.builder().name("custom:tenantId").value(tenantId)
                                                                        .build(),
                                                        AttributeType.builder().name("custom:role").value(role).build(),
                                                        AttributeType.builder().name("custom:tenantType")
                                                                        .value(tenantType.name()).build())
                                        .desiredDeliveryMediums(DeliveryMediumType.EMAIL)
                                        .messageAction(MessageActionType.SUPPRESS) // We'll send custom email
                                        .build();

                        AdminCreateUserResponse createResponse = cognitoClient.adminCreateUser(createRequest);

                        // Set permanent password
                        cognitoClient.adminSetUserPassword(b -> b
                                        .userPoolId(cognitoProperties.getUserPoolId())
                                        .username(email)
                                        .password(password)
                                        .permanent(true));

                        // Extract user's Cognito sub (unique ID) to use in user_roles table
                        String userId = createResponse.user().attributes().stream()
                                        .filter(attr -> "sub".equals(attr.name()))
                                        .map(AttributeType::value)
                                        .findFirst()
                                        .orElseThrow(() -> new RuntimeException(
                                                        "Failed to get user sub from Cognito response"));

                        // Assign role in database for authorization checks
                        userRoleService.assignRole(userId, role, "system");

                        log.info("Cognito user created and role assigned: email={} role={} userId={}",
                                        email, role, userId);

                } catch (UsernameExistsException e) {
                        throw new IllegalArgumentException("User with email " + email + " already exists");
                } catch (CognitoIdentityProviderException e) {
                        log.error("Cognito error: {}", e.awsErrorDetails().errorMessage());
                        throw new RuntimeException("Failed to create user: " + e.awsErrorDetails().errorMessage());
                }
        }

        // Helper: Calculate SECRET_HASH for Cognito API calls
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

        // Helper: Generate tenant ID for personal accounts
        private String generateTenantId(String email) {
                String username = email.split("@")[0];
                String sanitized = username.replaceAll("[^a-zA-Z0-9]", "");
                String timestamp = String.valueOf(System.currentTimeMillis()).substring(8);
                return "user-" + sanitized + "-" + timestamp;
        }

        // Helper: Slugify company name for tenant ID
        private String slugify(String companyName) {
                return companyName.toLowerCase()
                                .replaceAll("[^a-z0-9]+", "-")
                                .replaceAll("^-|-$", "");
        }

        // Helper: Validate corporate email (optional)
        private void validateCorporateEmail(String email) {
                List<String> freeProviders = List.of("gmail.com", "yahoo.com", "hotmail.com");
                String domain = email.substring(email.indexOf("@") + 1);

                if (freeProviders.contains(domain.toLowerCase())) {
                        throw new IllegalArgumentException(
                                        "Please use a corporate email address for organization signup");
                }
        }
}
