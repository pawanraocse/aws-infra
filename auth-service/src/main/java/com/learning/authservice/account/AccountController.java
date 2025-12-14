package com.learning.authservice.account;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for account management operations.
 */
@RestController
@RequestMapping("/api/v1/account")
@RequiredArgsConstructor
@Slf4j
public class AccountController {

        private final AccountDeletionService accountDeletionService;

        /**
         * Delete the current user's account and tenant.
         * This is a destructive operation that:
         * - Drops the tenant database
         * - Deletes the user from Cognito
         * - Creates an audit record for re-registration tracking
         */
        @PostMapping("/delete")
        public ResponseEntity<Map<String, String>> deleteAccount(
                        @Valid @RequestBody DeleteAccountRequest request,
                        HttpServletRequest httpRequest) {

                // Get user info from headers (set by Gateway)
                String tenantId = httpRequest.getHeader("X-Tenant-Id");
                String userId = httpRequest.getHeader("X-User-Id");

                if (tenantId == null || userId == null) {
                        log.warn("Missing required headers for account deletion");
                        return ResponseEntity.badRequest().body(Map.of(
                                        "error", "Missing required headers",
                                        "message", "X-Tenant-Id and X-User-Id headers required"));
                }

                // Validate confirmation
                if (!"DELETE".equalsIgnoreCase(request.confirmation())) {
                        log.warn("Invalid confirmation for account deletion: userId={}", userId);
                        return ResponseEntity.badRequest().body(Map.of(
                                        "error", "Invalid confirmation",
                                        "message", "Please type DELETE to confirm"));
                }

                log.info("Account deletion requested: tenantId={}, userId={}", tenantId, userId);

                try {
                        accountDeletionService.deleteAccount(tenantId, userId);

                        return ResponseEntity.ok(Map.of(
                                        "status", "DELETED",
                                        "message", "Account deleted successfully"));

                } catch (Exception e) {
                        log.error("Account deletion failed: tenantId={}, userId={}, error={}",
                                        tenantId, userId, e.getMessage(), e);
                        return ResponseEntity.internalServerError().body(Map.of(
                                        "error", "Deletion failed",
                                        "message", e.getMessage()));
                }
        }

        /**
         * Request body for account deletion.
         */
        public record DeleteAccountRequest(
                        @NotBlank(message = "Confirmation is required") String confirmation) {
        }
}
