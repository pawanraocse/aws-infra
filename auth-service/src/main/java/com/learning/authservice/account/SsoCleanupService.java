package com.learning.authservice.account;

import com.learning.authservice.config.CognitoProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.DeleteIdentityProviderRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ResourceNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for cleaning up SSO-related resources on account/tenant deletion.
 * 
 * Handles cleanup of:
 * - Cognito users (both native and federated identities)
 * - Cognito Identity Providers (SAML/OIDC)
 * 
 * Production Pattern:
 * This service performs synchronous cleanup. For high-volume production,
 * consider publishing to SNS/SQS for async processing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SsoCleanupService {

    private final CognitoIdentityProviderClient cognitoClient;
    private final CognitoProperties cognitoProperties;

    /**
     * Clean up all Cognito users associated with an email.
     * 
     * This finds ALL users with the given email, including:
     * - Native email/password users
     * - Federated identities (Google_xxx, okta-xxx, etc.)
     * 
     * @param email User's email address
     * @return Number of users deleted
     */
    public int cleanupCognitoUsersByEmail(String email) {
        log.info("Cleaning up Cognito users for email: {}", maskEmail(email));

        List<UserType> users = findUsersByEmail(email);
        int deleted = 0;

        for (UserType user : users) {
            try {
                deleteCognitoUser(user.username());
                deleted++;
            } catch (Exception e) {
                log.warn("Failed to delete Cognito user '{}': {}",
                        user.username(), e.getMessage());
            }
        }

        log.info("Cleaned up {} Cognito users for email: {}", deleted, maskEmail(email));
        return deleted;
    }

    /**
     * Find all Cognito users by email attribute.
     * This catches federated identities that have different usernames.
     */
    public List<UserType> findUsersByEmail(String email) {
        List<UserType> allUsers = new ArrayList<>();
        String paginationToken = null;

        try {
            do {
                ListUsersRequest.Builder requestBuilder = ListUsersRequest.builder()
                        .userPoolId(cognitoProperties.getUserPoolId())
                        .filter("email = \"" + email + "\"")
                        .limit(60);

                if (paginationToken != null) {
                    requestBuilder.paginationToken(paginationToken);
                }

                ListUsersResponse response = cognitoClient.listUsers(requestBuilder.build());
                allUsers.addAll(response.users());
                paginationToken = response.paginationToken();

            } while (paginationToken != null);

            log.debug("Found {} Cognito users for email: {}", allUsers.size(), maskEmail(email));
            return allUsers;

        } catch (Exception e) {
            log.error("Failed to list Cognito users by email: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Delete a single Cognito user by username.
     */
    public void deleteCognitoUser(String username) {
        log.info("Deleting Cognito user: {}", username);

        try {
            AdminDeleteUserRequest request = AdminDeleteUserRequest.builder()
                    .userPoolId(cognitoProperties.getUserPoolId())
                    .username(username)
                    .build();

            cognitoClient.adminDeleteUser(request);
            log.info("Deleted Cognito user: {}", username);

        } catch (UserNotFoundException e) {
            log.warn("Cognito user not found (already deleted?): {}", username);
        } catch (CognitoIdentityProviderException e) {
            log.error("Failed to delete Cognito user '{}': {}", username, e.getMessage());
            throw e;
        }
    }

    /**
     * Delete a Cognito Identity Provider for a tenant.
     * 
     * @param tenantId Tenant ID (used to derive provider name)
     * @param idpType  Type of IdP (SAML, OIDC, OKTA, etc.)
     */
    public void deleteIdentityProvider(String tenantId, String idpType) {
        // Generate provider name matching the creation pattern
        String sanitizedTenantId = tenantId.replaceAll("[^a-zA-Z0-9]", "");
        String providerName = idpType.toUpperCase() + "-" + sanitizedTenantId;

        log.info("Deleting Cognito Identity Provider: {}", providerName);

        try {
            DeleteIdentityProviderRequest request = DeleteIdentityProviderRequest.builder()
                    .userPoolId(cognitoProperties.getUserPoolId())
                    .providerName(providerName)
                    .build();

            cognitoClient.deleteIdentityProvider(request);
            log.info("Deleted Identity Provider: {}", providerName);

        } catch (ResourceNotFoundException e) {
            log.warn("Identity Provider not found (already deleted?): {}", providerName);
        } catch (CognitoIdentityProviderException e) {
            log.error("Failed to delete Identity Provider '{}': {}", providerName, e.getMessage());
            // Don't throw - log and continue with cleanup
        }
    }

    /**
     * Full SSO cleanup for a tenant.
     * 
     * Cleans up:
     * 1. Identity Provider (if exists)
     * 2. All users with the owner email
     * 
     * @param tenantId          Tenant ID
     * @param ownerEmail        Owner's email
     * @param idpType           IdP type (nullable)
     * @param shouldDeleteOwner Whether to delete owner's Cognito user
     */
    public void cleanupTenantSso(String tenantId, String ownerEmail,
            String idpType, boolean shouldDeleteOwner) {
        log.info("Starting SSO cleanup for tenant: {}", tenantId);

        // 1. Delete Identity Provider if configured
        if (idpType != null && !idpType.isBlank()) {
            deleteIdentityProvider(tenantId, idpType);
        }

        // 2. Delete owner's Cognito users only if this was their last membership
        if (shouldDeleteOwner) {
            cleanupCognitoUsersByEmail(ownerEmail);
        }

        log.info("SSO cleanup completed for tenant: {}", tenantId);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int atIndex = email.indexOf("@");
        if (atIndex <= 2) {
            return "***" + email.substring(atIndex);
        }
        return email.substring(0, 2) + "***" + email.substring(atIndex);
    }
}
