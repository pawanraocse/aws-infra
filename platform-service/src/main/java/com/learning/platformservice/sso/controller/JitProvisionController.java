package com.learning.platformservice.sso.controller;

import com.learning.platformservice.membership.entity.MembershipStatus;
import com.learning.platformservice.membership.entity.UserTenantMembership;
import com.learning.platformservice.membership.repository.UserTenantMembershipRepository;
import com.learning.platformservice.tenant.entity.Tenant;
import com.learning.platformservice.tenant.repo.TenantRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Internal controller for JIT (Just-In-Time) user provisioning.
 * Called by PreTokenGeneration Lambda during SSO login flow.
 *
 * NOTE: This is an internal endpoint - should only be accessible from within
 * VPC.
 */
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
@Slf4j
public class JitProvisionController {

        private final UserTenantMembershipRepository membershipRepository;
        private final TenantRepository tenantRepository;

        /**
         * Check if a user exists in a tenant.
         * Called by Lambda to determine if JIT provisioning is needed.
         */
        @GetMapping("/exists")
        public ResponseEntity<UserExistsResponse> checkUserExists(
                        @RequestParam String tenantId,
                        @RequestParam @Email String email) {
                log.debug("Checking if user exists: tenant={}, email={}", tenantId, email);

                boolean exists = membershipRepository
                                .findByEmailAndTenant(email, tenantId)
                                .filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
                                .isPresent();

                return ResponseEntity.ok(new UserExistsResponse(exists, tenantId, email));
        }

        /**
         * JIT provision a new SSO user.
         * Creates membership and user records for first-time SSO login.
         */
        @PostMapping("/jit-provision")
        public ResponseEntity<JitProvisionResponse> provisionUser(
                        @Valid @RequestBody JitProvisionRequest request) {
                log.info("JIT provisioning user: tenant={}, email={}, source={}",
                                request.tenantId(), request.email(), request.source());

                // Validate tenant exists
                Tenant tenant = tenantRepository.findById(request.tenantId())
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "Tenant not found: " + request.tenantId()));

                // Check if user already exists (case-insensitive)
                var existingMembership = membershipRepository.findByEmailAndTenant(
                                request.email().toLowerCase(), request.tenantId());
                if (existingMembership.isPresent()) {
                        log.info("User already exists, skipping JIT provision: {}", request.email());
                        return ResponseEntity.ok(new JitProvisionResponse(
                                        true, "User already exists", request.email(), request.tenantId()));
                }

                try {
                        // Create membership - do NOT set ID, let Hibernate generate it
                        UserTenantMembership membership = new UserTenantMembership();
                        membership.setUserEmail(request.email().toLowerCase());
                        membership.setTenantId(request.tenantId());
                        membership.setCognitoUserId(request.cognitoUserId());
                        membership.setRoleHint(request.defaultRole() != null ? request.defaultRole() : "admin");
                        membership.setIsOwner(false);
                        membership.setIsDefault(true); // SSO users get their tenant as default
                        membership.setStatus(MembershipStatus.ACTIVE);
                        membership.setJoinedAt(OffsetDateTime.now());
                        // Note: invitedBy is null for SSO users

                        membershipRepository.save(membership);

                        log.info("Successfully JIT provisioned user: {} in tenant: {}",
                                        request.email(), request.tenantId());

                        return ResponseEntity.ok(new JitProvisionResponse(
                                        true, "User provisioned successfully", request.email(), request.tenantId()));
                } catch (Exception e) {
                        // Handle race condition - if another request already created the user
                        // Check both the exception and its root cause
                        Throwable rootCause = e;
                        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
                                rootCause = rootCause.getCause();
                        }

                        String exceptionName = e.getClass().getSimpleName();
                        String rootCauseName = rootCause.getClass().getSimpleName();
                        String message = e.getMessage() != null ? e.getMessage() : "";
                        String rootMessage = rootCause.getMessage() != null ? rootCause.getMessage() : "";

                        boolean isUserExistsError = exceptionName.contains("StaleObjectState") ||
                                        exceptionName.contains("OptimisticLocking") ||
                                        exceptionName.contains("DataIntegrity") ||
                                        rootCauseName.contains("StaleObjectState") ||
                                        rootCauseName.contains("OptimisticLocking") ||
                                        message.contains("duplicate key") ||
                                        message.contains("already exists") ||
                                        rootMessage.contains("duplicate key") ||
                                        rootMessage.contains("already exists");

                        if (isUserExistsError) {
                                log.info("User already provisioned (concurrent request): {} - {}", request.email(),
                                                rootCauseName);
                                return ResponseEntity.ok(new JitProvisionResponse(
                                                true, "User already exists", request.email(), request.tenantId()));
                        }
                        log.error("Failed to provision user: {}", request.email(), e);
                        throw e;
                }
        }

        // ========== DTOs ==========

        public record UserExistsResponse(
                        boolean exists,
                        String tenantId,
                        String email) {
        }

        public record JitProvisionRequest(
                        @NotBlank(message = "Tenant ID is required") String tenantId,

                        @NotBlank(message = "Email is required") @Email(message = "Valid email required") String email,

                        String cognitoUserId,

                        List<String> groups,

                        @NotBlank(message = "Source is required") String source, // SAML, OIDC, etc.

                        String defaultRole) {
        }

        public record JitProvisionResponse(
                        boolean success,
                        String message,
                        String email,
                        String tenantId) {
        }
}
