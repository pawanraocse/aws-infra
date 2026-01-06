package com.learning.authservice.sso.service;

import com.learning.authservice.config.CognitoProperties;
import com.learning.common.dto.IdpType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CreateIdentityProviderRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.DeleteIdentityProviderRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.DescribeIdentityProviderRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.DescribeIdentityProviderResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.DescribeUserPoolClientRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.DescribeUserPoolClientResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.IdentityProviderTypeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ResourceNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UpdateIdentityProviderRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UpdateUserPoolClientRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Manages AWS Cognito Identity Provider operations.
 * 
 * Extracted from SsoConfigurationServiceImpl to follow SRP (Single
 * Responsibility Principle).
 * This class handles all direct Cognito SDK interactions for identity
 * providers.
 * 
 * SOLID Compliance:
 * - SRP: Only handles Cognito IdP CRUD operations
 * - OCP: New IdP types can be added via the buildProviderName switch
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CognitoProviderManager {

    private final CognitoIdentityProviderClient cognitoClient;
    private final CognitoProperties cognitoProperties;

    /**
     * Create or update a Cognito Identity Provider.
     * If provider exists, updates it. Otherwise, creates new one.
     */
    public void createOrUpdateProvider(String providerName, IdentityProviderTypeType providerType,
            Map<String, String> providerDetails, Map<String, String> attributeMapping) {
        try {
            cognitoClient.describeIdentityProvider(DescribeIdentityProviderRequest.builder()
                    .userPoolId(cognitoProperties.getUserPoolId())
                    .providerName(providerName)
                    .build());

            // Provider exists, update it
            cognitoClient.updateIdentityProvider(UpdateIdentityProviderRequest.builder()
                    .userPoolId(cognitoProperties.getUserPoolId())
                    .providerName(providerName)
                    .providerDetails(providerDetails)
                    .attributeMapping(attributeMapping)
                    .build());

            log.info("Updated Cognito identity provider: {}", providerName);

        } catch (ResourceNotFoundException e) {
            // Provider doesn't exist, create it
            cognitoClient.createIdentityProvider(CreateIdentityProviderRequest.builder()
                    .userPoolId(cognitoProperties.getUserPoolId())
                    .providerName(providerName)
                    .providerType(providerType)
                    .providerDetails(providerDetails)
                    .attributeMapping(attributeMapping)
                    .build());

            log.info("Created Cognito identity provider: {}", providerName);
            registerProviderWithClient(providerName);
        }
    }

    /**
     * Delete a Cognito Identity Provider.
     */
    public void deleteProvider(String providerName) {
        try {
            cognitoClient.deleteIdentityProvider(DeleteIdentityProviderRequest.builder()
                    .userPoolId(cognitoProperties.getUserPoolId())
                    .providerName(providerName)
                    .build());
            log.info("Deleted Cognito identity provider: {}", providerName);
        } catch (ResourceNotFoundException e) {
            log.warn("Cognito provider not found for deletion: {}", providerName);
        }
    }

    /**
     * Describe a Cognito Identity Provider for testing/verification.
     */
    public DescribeIdentityProviderResponse describeProvider(String providerName) {
        return cognitoClient.describeIdentityProvider(
                DescribeIdentityProviderRequest.builder()
                        .userPoolId(cognitoProperties.getUserPoolId())
                        .providerName(providerName)
                        .build());
    }

    /**
     * Register the identity provider with the user pool client.
     * This allows the provider to be used for authentication.
     */
    private void registerProviderWithClient(String providerName) {
        try {
            String clientId = cognitoProperties.getClientId();
            DescribeUserPoolClientResponse clientResponse = cognitoClient.describeUserPoolClient(
                    DescribeUserPoolClientRequest.builder()
                            .userPoolId(cognitoProperties.getUserPoolId())
                            .clientId(clientId)
                            .build());

            var client = clientResponse.userPoolClient();
            List<String> currentProviders = new ArrayList<>(
                    client.supportedIdentityProviders() != null
                            ? client.supportedIdentityProviders()
                            : List.of("COGNITO"));

            if (currentProviders.contains(providerName)) {
                return;
            }

            currentProviders.add(providerName);

            cognitoClient.updateUserPoolClient(UpdateUserPoolClientRequest.builder()
                    .userPoolId(cognitoProperties.getUserPoolId())
                    .clientId(clientId)
                    .clientName(client.clientName())
                    .supportedIdentityProviders(currentProviders)
                    .callbackURLs(client.callbackURLs())
                    .logoutURLs(client.logoutURLs())
                    .allowedOAuthFlows(client.allowedOAuthFlows())
                    .allowedOAuthScopes(client.allowedOAuthScopes())
                    .allowedOAuthFlowsUserPoolClient(client.allowedOAuthFlowsUserPoolClient())
                    .explicitAuthFlows(client.explicitAuthFlows())
                    .accessTokenValidity(client.accessTokenValidity())
                    .idTokenValidity(client.idTokenValidity())
                    .refreshTokenValidity(client.refreshTokenValidity())
                    .tokenValidityUnits(client.tokenValidityUnits())
                    .readAttributes(client.readAttributes())
                    .writeAttributes(client.writeAttributes())
                    .preventUserExistenceErrors(client.preventUserExistenceErrors())
                    .enableTokenRevocation(client.enableTokenRevocation())
                    .build());

            log.info("Registered identity provider {} with client {}", providerName, clientId);
        } catch (Exception ex) {
            log.error("Failed to register provider {}: {}", providerName, ex.getMessage(), ex);
        }
    }

    /**
     * Build a unique provider name based on tenant and IdP type.
     */
    public String buildProviderName(String tenantId, IdpType idpType) {
        String sanitizedTenantId = tenantId.replaceAll("[^a-zA-Z0-9]", "");
        if (sanitizedTenantId.length() > 20) {
            sanitizedTenantId = sanitizedTenantId.substring(0, 20);
        }

        String providerPrefix = switch (idpType) {
            case GOOGLE -> "GWORKSPACE";
            case GOOGLE_SAML -> "GSAML";
            case AZURE_AD -> "AZUREOIDC";
            default -> idpType.name().replace("_", "");
        };
        return providerPrefix + "-" + sanitizedTenantId;
    }

    /**
     * Map IdpType enum to Cognito IdentityProviderTypeType.
     */
    public IdentityProviderTypeType mapIdpTypeToProviderType(IdpType idpType) {
        return switch (idpType) {
            case GOOGLE, AZURE_AD, OKTA, OIDC -> IdentityProviderTypeType.OIDC;
            case GOOGLE_SAML, SAML, PING -> IdentityProviderTypeType.SAML;
            default -> IdentityProviderTypeType.OIDC;
        };
    }

    /**
     * Get Cognito domain for SAML metadata.
     */
    public String getCognitoDomain() {
        return cognitoProperties.getDomain();
    }

    /**
     * Get User Pool ID.
     */
    public String getUserPoolId() {
        return cognitoProperties.getUserPoolId();
    }
}
