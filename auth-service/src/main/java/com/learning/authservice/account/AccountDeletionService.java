package com.learning.authservice.account;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Service for handling account deletion with SSO-aware cleanup.
 * 
 * Flow:
 * 1. Call platform-service to delete tenant (marks memberships REMOVED)
 * 2. Check if user has other active memberships
 * 3. Clean up SSO resources (IdP, federated users) if configured
 * 4. Only delete Cognito users if this was their LAST membership
 * 
 * IMPORTANT: Uses SsoCleanupService to properly find and delete ALL
 * Cognito identities for a user (including federated identities like
 * Google_xxx, okta-xxx, etc.), not just email-based users.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountDeletionService {

    private final SsoCleanupService ssoCleanupService;
    private final WebClient.Builder webClientBuilder;

    @Value("${services.platform-service.url:http://platform-service:8083}")
    private String platformServiceUrl;

    /**
     * Delete user's account and tenant with full SSO cleanup.
     * 
     * @param tenantId  Tenant to delete (from JWT)
     * @param userEmail User's email
     * @param idpType   IdP type (nullable - only set if SSO was configured)
     * @throws RuntimeException if deletion fails
     */
    public void deleteAccount(String tenantId, String userEmail, String idpType) {
        log.info("Deleting account: tenantId={}, userEmail={}, hasSSO={}",
                tenantId, userEmail, idpType != null);

        // 1. Delete tenant via platform-service (marks memberships as REMOVED)
        deleteTenantViaPlatformService(tenantId, userEmail);

        // 2. Check if user has OTHER active memberships remaining
        long remainingMemberships = countRemainingMemberships(userEmail);
        boolean isLastMembership = remainingMemberships == 0;

        // 3. Clean up SSO resources (IdP configuration)
        if (idpType != null && !idpType.isBlank()) {
            log.info("Cleaning up SSO Identity Provider for tenant: {}", tenantId);
            ssoCleanupService.deleteIdentityProvider(tenantId, idpType);
        }

        // 4. Only delete Cognito users if this was their LAST membership
        // Uses SSO-aware cleanup that finds ALL identities (native + federated)
        if (isLastMembership) {
            log.info("Last membership deleted, cleaning up Cognito users for: {}", userEmail);
            int deleted = ssoCleanupService.cleanupCognitoUsersByEmail(userEmail);
            log.info("Cleaned up {} Cognito identities for user", deleted);
        } else {
            log.info("User still has {} other active membership(s), keeping Cognito users",
                    remainingMemberships);
        }

        log.info("Account deletion completed: tenantId={}, cognitoDeleted={}",
                tenantId, isLastMembership);
    }

    /**
     * Backward-compatible overload without IdP type.
     */
    public void deleteAccount(String tenantId, String userEmail) {
        deleteAccount(tenantId, userEmail, null);
    }

    /**
     * Query platform-service to count remaining active memberships.
     */
    private long countRemainingMemberships(String userEmail) {
        log.debug("Checking remaining memberships for user");

        try {
            WebClient webClient = webClientBuilder.baseUrl(platformServiceUrl).build();

            @SuppressWarnings("unchecked")
            Map<String, Long> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/memberships/count-active")
                            .queryParam("email", userEmail)
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, clientResponse -> {
                        log.error("Failed to count memberships: status={}", clientResponse.statusCode());
                        return clientResponse.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new RuntimeException(
                                        "Failed to count memberships: " + body)));
                    })
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && response.containsKey("count")) {
                Number count = response.get("count");
                return count != null ? count.longValue() : 0L;
            }
            return 0L;

        } catch (Exception e) {
            log.error("Failed to count memberships: {}", e.getMessage(), e);
            // Fail-safe: don't delete Cognito user if we can't verify count
            throw new RuntimeException("Cannot verify remaining memberships: " + e.getMessage(), e);
        }
    }

    private void deleteTenantViaPlatformService(String tenantId, String deletedBy) {
        log.info("Calling platform-service to delete tenant: {}", tenantId);

        try {
            WebClient webClient = webClientBuilder.baseUrl(platformServiceUrl).build();

            webClient.delete()
                    .uri("/platform/internal/tenants/{tenantId}", tenantId)
                    .header("X-Deleted-By", deletedBy)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response -> {
                        if (response.statusCode() == HttpStatus.NOT_FOUND) {
                            log.warn("Tenant not found in platform-service: {}", tenantId);
                            return Mono.empty();
                        }
                        return response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new RuntimeException(
                                        "Platform service error: " + response.statusCode() + " - " + body)));
                    })
                    .bodyToMono(String.class)
                    .block();

            log.info("Tenant deleted via platform-service: {}", tenantId);

        } catch (Exception e) {
            log.error("Failed to delete tenant: {} - {}", tenantId, e.getMessage(), e);
            throw new RuntimeException("Failed to delete tenant: " + e.getMessage(), e);
        }
    }
}
