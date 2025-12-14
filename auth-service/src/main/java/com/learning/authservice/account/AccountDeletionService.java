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

/**
 * Service for handling account deletion.
 * 
 * Flow:
 * 1. Call platform-service to delete tenant (DB drop, audit record)
 * 2. Delete user from Cognito
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
     * @param tenantId  Tenant to delete (from JWT)
     * @param userEmail User's email
     * @throws RuntimeException if deletion fails
     */
    public void deleteAccount(String tenantId, String userEmail) {
        log.info("Deleting account: tenantId={}, userEmail={}", tenantId, userEmail);

        // 1. Delete tenant via platform-service
        deleteTenantViaPlatformService(tenantId, userEmail);

        // 2. Delete user from Cognito
        deleteCognitoUser(userEmail);

        log.info("Account deletion completed: tenantId={}, userEmail={}", tenantId, userEmail);
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
