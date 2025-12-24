package com.learning.authservice.account;

import com.learning.authservice.config.CognitoProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;

import java.util.Map;

/**
 * Service for handling account deletion.
 * 
 * <p>
 * Flow:
 * </p>
 * <ol>
 * <li>Call platform-service to delete tenant (DB drop, audit record)</li>
 * <li>Check if user has other active memberships</li>
 * <li>Only delete user from Cognito if this was their LAST membership</li>
 * </ol>
 * 
 * <p>
 * <b>IMPORTANT:</b> This prevents the bug where deleting one account
 * would break all other accounts for the same email.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountDeletionService {

    private final CognitoIdentityProviderClient cognitoClient;
    private final CognitoProperties cognitoProperties;
    private final WebClient.Builder webClientBuilder;

    @Value("${services.platform-service.url:http://platform-service:8083}")
    private String platformServiceUrl;

    /**
     * Delete user's account and tenant.
     * 
     * <p>
     * Only deletes the Cognito user if this was their last active membership.
     * Otherwise, the user keeps their Cognito account for other tenants.
     * </p>
     * 
     * @param tenantId  Tenant to delete (from JWT)
     * @param userEmail User's email
     * @throws RuntimeException if deletion fails
     */
    public void deleteAccount(String tenantId, String userEmail) {
        log.info("Deleting account: tenantId={}, userEmail={}", tenantId, userEmail);

        // 1. Delete tenant via platform-service (this also marks memberships as
        // REMOVED)
        deleteTenantViaPlatformService(tenantId, userEmail);

        // 2. Check if user has OTHER active memberships remaining
        long remainingMemberships = countRemainingMemberships(userEmail);

        // 3. Only delete Cognito user if this was their LAST membership
        if (remainingMemberships == 0) {
            log.info("Last membership deleted, removing Cognito user: {}", userEmail);
            deleteCognitoUser(userEmail);
        } else {
            log.info("User still has {} other active membership(s), keeping Cognito user: {}",
                    remainingMemberships, userEmail);
        }

        log.info("Account deletion completed: tenantId={}, userEmail={}, cognitoDeleted={}",
                tenantId, userEmail, remainingMemberships == 0);
    }

    /**
     * Query platform-service to count remaining active memberships.
     * 
     * @param userEmail User's email
     * @return Count of active memberships (0 if last one was just deleted)
     */
    private long countRemainingMemberships(String userEmail) {
        log.debug("Checking remaining memberships for: {}", userEmail);

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
                // Handle both Integer and Long since JSON might deserialize as Integer
                Number count = response.get("count");
                return count != null ? count.longValue() : 0L;
            }
            return 0L;

        } catch (Exception e) {
            log.error("Failed to count memberships for {}: {}", userEmail, e.getMessage(), e);
            // Fail-safe: don't delete Cognito user if we can't verify count
            log.warn("Fail-safe: Not deleting Cognito user due to membership count error");
            throw new RuntimeException("Cannot verify remaining memberships: " + e.getMessage(), e);
        }
    }

    private void deleteTenantViaPlatformService(String tenantId, String deletedBy) {
        log.info("Calling platform-service to delete tenant: tenantId={}", tenantId);

        try {
            WebClient webClient = webClientBuilder.baseUrl(platformServiceUrl).build();

            webClient.delete()
                    .uri("/platform/internal/tenants/{tenantId}", tenantId)
                    .header("X-Deleted-By", deletedBy)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response -> {
                        if (response.statusCode() == HttpStatus.NOT_FOUND) {
                            log.warn("Tenant not found in platform-service: tenantId={}", tenantId);
                            return Mono.empty(); // Tenant already deleted, continue
                        }
                        return response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new RuntimeException(
                                        "Platform service error: " + response.statusCode() + " - " + body)));
                    })
                    .bodyToMono(String.class)
                    .block();

            log.info("Tenant deleted via platform-service: tenantId={}", tenantId);

        } catch (Exception e) {
            log.error("Failed to delete tenant via platform-service: tenantId={}, error={}",
                    tenantId, e.getMessage(), e);
            throw new RuntimeException("Failed to delete tenant: " + e.getMessage(), e);
        }
    }

    private void deleteCognitoUser(String username) {
        log.info("Deleting user from Cognito: username={}", username);

        try {
            AdminDeleteUserRequest deleteRequest = AdminDeleteUserRequest.builder()
                    .userPoolId(cognitoProperties.getUserPoolId())
                    .username(username)
                    .build();

            AdminDeleteUserResponse response = cognitoClient.adminDeleteUser(deleteRequest);
            log.info("User deleted from Cognito: username={}, sdkResponse={}",
                    username, response.sdkHttpResponse().statusCode());

        } catch (CognitoIdentityProviderException e) {
            if (e.awsErrorDetails().errorCode().equals("UserNotFoundException")) {
                log.warn("User not found in Cognito (already deleted?): username={}", username);
                return; // User already deleted, continue
            }
            log.error("Failed to delete Cognito user: username={}, error={}", username, e.getMessage(), e);
            throw new RuntimeException("Failed to delete Cognito user: " + e.getMessage(), e);
        }
    }
}
