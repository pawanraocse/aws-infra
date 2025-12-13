package com.learning.platformservice.membership.api;

import com.learning.platformservice.membership.dto.AddMembershipRequest;
import com.learning.platformservice.membership.dto.TenantLookupResponse;
import com.learning.platformservice.membership.entity.UserTenantMembership;
import com.learning.platformservice.membership.service.MembershipService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Internal API for user-tenant membership operations.
 * 
 * <p>
 * These endpoints are called by auth-service and other internal services.
 * They are not exposed through the API Gateway to external clients.
 * </p>
 * 
 * <h3>Endpoints:</h3>
 * <ul>
 * <li>GET /internal/memberships/by-email - Lookup tenants for login flow</li>
 * <li>POST /internal/memberships - Create new membership</li>
 * <li>PATCH /internal/memberships/last-accessed - Update last accessed
 * timestamp</li>
 * <li>GET /internal/memberships/can-create-personal - Check personal tenant
 * limit</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/memberships")
@RequiredArgsConstructor
@Slf4j
public class TenantMembershipInternalController {

    private final MembershipService membershipService;

    /**
     * Find all tenants for a given email address.
     * Used during login to populate the tenant selector.
     *
     * @param email User's email address
     * @return List of tenants the user can access
     */
    @GetMapping("/by-email")
    public ResponseEntity<List<TenantLookupResponse>> findTenantsByEmail(
            @RequestParam @NotBlank @Email String email) {

        log.debug("Internal API: findTenantsByEmail called");
        List<TenantLookupResponse> tenants = membershipService.findTenantsByEmail(email);
        return ResponseEntity.ok(tenants);
    }

    /**
     * Add a new membership record.
     * Called during signup and invitation acceptance.
     *
     * @param request Membership details
     * @return Created response or conflict if already exists
     */
    @PostMapping
    public ResponseEntity<?> addMembership(@Valid @RequestBody AddMembershipRequest request) {
        log.info("Internal API: addMembership called for tenantId={}", request.tenantId());

        try {
            UserTenantMembership membership = membershipService.addMembership(request);
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(Map.of(
                            "id", membership.getId(),
                            "email", membership.getUserEmail(),
                            "tenantId", membership.getTenantId(),
                            "isDefault", membership.getIsDefault()));
        } catch (IllegalStateException e) {
            log.warn("Membership creation conflict: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Update last accessed timestamp when user logs into a tenant.
     * Called after successful authentication.
     *
     * @param email    User's email
     * @param tenantId Tenant that was accessed
     */
    @PatchMapping("/last-accessed")
    public ResponseEntity<Void> updateLastAccessed(
            @RequestParam @NotBlank @Email String email,
            @RequestParam @NotBlank String tenantId) {

        log.debug("Internal API: updateLastAccessed for tenantId={}", tenantId);
        membershipService.updateLastAccessed(email, tenantId);
        return ResponseEntity.ok().build();
    }

    /**
     * Check if a user can create a personal tenant.
     * Rule: Only 1 personal tenant per email.
     *
     * @param email User's email to check
     * @return true if user can create a personal tenant
     */
    @GetMapping("/can-create-personal")
    public ResponseEntity<Map<String, Boolean>> canCreatePersonalTenant(
            @RequestParam @NotBlank @Email String email) {

        boolean canCreate = membershipService.canCreatePersonalTenant(email);
        return ResponseEntity.ok(Map.of("canCreate", canCreate));
    }

    /**
     * Update Cognito user ID for all memberships of an email.
     * Called after first login when we have the Cognito ID.
     */
    @PatchMapping("/cognito-id")
    public ResponseEntity<Void> updateCognitoId(
            @RequestParam @NotBlank @Email String email,
            @RequestParam @NotBlank String cognitoUserId) {

        log.debug("Internal API: updateCognitoId for email");
        membershipService.updateCognitoId(email, cognitoUserId);
        return ResponseEntity.ok().build();
    }
}
