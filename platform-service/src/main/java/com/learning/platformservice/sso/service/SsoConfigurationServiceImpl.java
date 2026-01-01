package com.learning.platformservice.sso.service;

import com.learning.platformservice.config.CognitoProperties;
import com.learning.platformservice.sso.dto.OidcConfigRequest;
import com.learning.platformservice.sso.dto.SamlConfigRequest;
import com.learning.platformservice.sso.dto.SsoConfigDto;
import com.learning.platformservice.sso.dto.SsoTestResult;
import com.learning.platformservice.tenant.entity.IdpType;
import com.learning.platformservice.tenant.entity.Tenant;
import com.learning.platformservice.tenant.repo.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CreateIdentityProviderRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.DeleteIdentityProviderRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.DescribeIdentityProviderRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.DescribeIdentityProviderResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.DescribeUserPoolClientRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.DescribeUserPoolClientResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.IdentityProviderType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.IdentityProviderTypeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ResourceNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UpdateIdentityProviderRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UpdateUserPoolClientRequest;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Implementation of SSO configuration service.
 * Uses AWS Cognito SDK to manage identity providers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SsoConfigurationServiceImpl implements SsoConfigurationService {

    private final TenantRepository tenantRepository;
    private final CognitoIdentityProviderClient cognitoClient;
    private final CognitoProperties cognitoProperties;

    @Override
    public Optional<SsoConfigDto> getConfiguration(String tenantId) {
        return tenantRepository.findById(tenantId)
                .filter(tenant -> tenant.getIdpType() != null)
                .map(this::toSsoConfigDto);
    }

    @Override
    @Transactional
    public SsoConfigDto saveSamlConfiguration(String tenantId, SamlConfigRequest request) {
        log.info("Configuring SAML SSO for tenant: {}, provider: {}", tenantId, request.providerName());

        Tenant tenant = getTenantOrThrow(tenantId);

        // Build Cognito Identity Provider name (must be unique within pool)
        String providerName = buildProviderName(tenantId, request.idpType());

        // Prepare provider details for SAML
        Map<String, String> providerDetails = new HashMap<>();
        if (request.metadataUrl() != null && !request.metadataUrl().isBlank()) {
            providerDetails.put("MetadataURL", request.metadataUrl());
        } else if (request.metadataXml() != null && !request.metadataXml().isBlank()) {
            providerDetails.put("MetadataFile", request.metadataXml());
        }
        providerDetails.put("IDPSignout", "false");

        // Prepare attribute mapping
        Map<String, String> attributeMapping = buildDefaultSamlAttributeMapping();
        if (request.attributeMappings() != null) {
            attributeMapping.putAll(request.attributeMappings());
        }

        // Create or update Cognito Identity Provider
        createOrUpdateCognitoProvider(providerName, IdentityProviderTypeType.SAML, providerDetails, attributeMapping);

        // Update tenant record
        tenant.setIdpType(request.idpType());
        tenant.setIdpMetadataUrl(request.metadataUrl());
        tenant.setIdpEntityId(request.entityId());
        tenant.setSsoEnabled(true);

        // Store additional config in JSONB
        Map<String, Object> idpConfig = new HashMap<>();
        idpConfig.put("providerName", request.providerName());
        idpConfig.put("cognitoProviderName", providerName);
        idpConfig.put("jitProvisioningEnabled", request.jitProvisioningEnabled());
        idpConfig.put("defaultRole", request.defaultRole() != null ? request.defaultRole() : "user");
        idpConfig.put("attributeMappings", attributeMapping);
        tenant.setIdpConfigJson(idpConfig);

        tenant.setUpdatedAt(OffsetDateTime.now());
        tenantRepository.save(tenant);

        log.info("SAML SSO configured successfully for tenant: {}", tenantId);
        return toSsoConfigDto(tenant);
    }

    @Override
    @Transactional
    public SsoConfigDto saveOidcConfiguration(String tenantId, OidcConfigRequest request) {
        log.info("Configuring OIDC SSO for tenant: {}, provider: {}", tenantId, request.providerName());

        Tenant tenant = getTenantOrThrow(tenantId);

        // Build provider name
        String providerName = buildProviderName(tenantId, request.idpType());

        // Determine provider type and details based on IdpType
        IdentityProviderTypeType providerType = mapIdpTypeToProviderType(request.idpType());
        Map<String, String> providerDetails = buildOidcProviderDetails(request);

        // Prepare attribute mapping
        Map<String, String> attributeMapping = buildDefaultOidcAttributeMapping();
        if (request.attributeMappings() != null) {
            attributeMapping.putAll(request.attributeMappings());
        }

        // Create or update Cognito Identity Provider
        createOrUpdateCognitoProvider(providerName, providerType, providerDetails, attributeMapping);

        // Update tenant record
        tenant.setIdpType(request.idpType());
        tenant.setSsoEnabled(true);

        // Store config in JSONB (note: client secret is NOT stored in DB)
        Map<String, Object> idpConfig = new HashMap<>();
        idpConfig.put("providerName", request.providerName());
        idpConfig.put("cognitoProviderName", providerName);
        idpConfig.put("oidcIssuer", request.issuerUrl());
        idpConfig.put("oidcClientId", request.clientId());
        idpConfig.put("jitProvisioningEnabled", request.jitProvisioningEnabled());
        idpConfig.put("defaultRole", request.defaultRole() != null ? request.defaultRole() : "user");
        idpConfig.put("attributeMappings", attributeMapping);
        tenant.setIdpConfigJson(idpConfig);

        tenant.setUpdatedAt(OffsetDateTime.now());
        tenantRepository.save(tenant);

        log.info("OIDC SSO configured successfully for tenant: {}", tenantId);
        return toSsoConfigDto(tenant);
    }

    @Override
    @Transactional
    public SsoConfigDto toggleSso(String tenantId, boolean enabled) {
        Tenant tenant = getTenantOrThrow(tenantId);
        tenant.setSsoEnabled(enabled);
        tenant.setUpdatedAt(OffsetDateTime.now());
        tenantRepository.save(tenant);

        log.info("SSO {} for tenant: {}", enabled ? "enabled" : "disabled", tenantId);
        return toSsoConfigDto(tenant);
    }

    @Override
    @Transactional
    public void deleteConfiguration(String tenantId) {
        Tenant tenant = getTenantOrThrow(tenantId);

        // Delete Cognito Identity Provider if exists
        if (tenant.getIdpConfigJson() != null) {
            String cognitoProviderName = (String) tenant.getIdpConfigJson().get("cognitoProviderName");
            if (cognitoProviderName != null) {
                try {
                    deleteCognitoProvider(cognitoProviderName);
                } catch (ResourceNotFoundException e) {
                    log.warn("Cognito provider not found: {}", cognitoProviderName);
                }
            }
        }

        // Clear SSO fields
        tenant.setSsoEnabled(false);
        tenant.setIdpType(null);
        tenant.setIdpMetadataUrl(null);
        tenant.setIdpEntityId(null);
        tenant.setIdpConfigJson(null);
        tenant.setUpdatedAt(OffsetDateTime.now());
        tenantRepository.save(tenant);

        log.info("SSO configuration deleted for tenant: {}", tenantId);
    }

    @Override
    public SsoTestResult testConnection(String tenantId) {
        long startTime = System.currentTimeMillis();

        try {
            Tenant tenant = getTenantOrThrow(tenantId);

            if (tenant.getIdpConfigJson() == null) {
                return SsoTestResult.builder()
                        .success(false)
                        .message("SSO not configured for this tenant")
                        .responseTimeMs(System.currentTimeMillis() - startTime)
                        .build();
            }

            String cognitoProviderName = (String) tenant.getIdpConfigJson().get("cognitoProviderName");

            // Describe the provider to verify it exists and is valid
            DescribeIdentityProviderResponse response = cognitoClient.describeIdentityProvider(
                    DescribeIdentityProviderRequest.builder()
                            .userPoolId(cognitoProperties.getUserPoolId())
                            .providerName(cognitoProviderName)
                            .build());

            IdentityProviderType provider = response.identityProvider();

            // Update test status in idpConfigJson
            Map<String, Object> config = new HashMap<>(tenant.getIdpConfigJson());
            config.put("lastTestedAt", java.time.OffsetDateTime.now().toString());
            config.put("testStatus", "SUCCESS");
            tenant.setIdpConfigJson(config);
            tenantRepository.save(tenant);

            return SsoTestResult.builder()
                    .success(true)
                    .message("Connection successful")
                    .providerName(provider.providerName())
                    .entityId(provider.providerDetails().get("IDPEntityId"))
                    .availableAttributes(new ArrayList<>(provider.attributeMapping().keySet()))
                    .responseTimeMs(System.currentTimeMillis() - startTime)
                    .build();

        } catch (ResourceNotFoundException e) {
            updateTestStatus(tenantId, "FAILED");
            return SsoTestResult.builder()
                    .success(false)
                    .message("Identity provider not found in Cognito")
                    .responseTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        } catch (Exception e) {
            log.error("SSO test failed for tenant: {}", tenantId, e);
            updateTestStatus(tenantId, "FAILED");
            return SsoTestResult.builder()
                    .success(false)
                    .message("Test failed: " + e.getMessage())
                    .responseTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }
    }

    private void updateTestStatus(String tenantId, String status) {
        try {
            Tenant tenant = getTenantOrThrow(tenantId);
            if (tenant.getIdpConfigJson() != null) {
                Map<String, Object> config = new HashMap<>(tenant.getIdpConfigJson());
                config.put("lastTestedAt", java.time.OffsetDateTime.now().toString());
                config.put("testStatus", status);
                tenant.setIdpConfigJson(config);
                tenantRepository.save(tenant);
            }
        } catch (Exception ex) {
            log.warn("Failed to update test status for tenant: {}", tenantId, ex);
        }
    }

    @Override
    public String getSpMetadata(String tenantId) {
        // Construct the SP metadata URL for this user pool
        String domain = getCognitoDomain();
        String metadataUrl = String.format(
                "https://%s.auth.%s.amazoncognito.com/saml2/idpmetadata?client_id=%s",
                domain, cognitoProperties.getRegion(), getDefaultClientId());

        // In production, you might fetch and return the actual XML
        // For now, return instructions
        return String.format("""
                SAML Service Provider Metadata

                ACS URL: https://%s.auth.%s.amazoncognito.com/saml2/idpresponse
                Entity ID: urn:amazon:cognito:sp:%s
                Metadata URL: %s
                """, domain, cognitoProperties.getRegion(), cognitoProperties.getUserPoolId(), metadataUrl);
    }

    // ========== Private Helpers ==========

    private Tenant getTenantOrThrow(String tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));
    }

    private String buildProviderName(String tenantId, IdpType idpType) {
        // Provider name: alphanumeric, must NOT contain underscores (Cognito
        // constraint)
        // Pattern: [^\p{Z}][\p{L}\p{M}\p{S}\p{N}\p{P}][^\p{Z}]+
        String sanitizedTenantId = tenantId.replaceAll("[^a-zA-Z0-9]", "");
        if (sanitizedTenantId.length() > 20) {
            sanitizedTenantId = sanitizedTenantId.substring(0, 20);
        }

        // Provider prefixes:
        // - GWORKSPACE: Google OIDC (no groups)
        // - GSAML: Google SAML (with groups)
        // - Others use IdpType name directly
        String providerPrefix = switch (idpType) {
            case GOOGLE -> "GWORKSPACE";
            case GOOGLE_SAML -> "GSAML";
            default -> idpType.name();
        };
        return providerPrefix + "-" + sanitizedTenantId;
    }

    private void createOrUpdateCognitoProvider(String providerName, IdentityProviderTypeType providerType,
            Map<String, String> providerDetails,
            Map<String, String> attributeMapping) {
        try {
            // Try to describe to check if exists
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

            // Register the new provider with the SPA client
            registerProviderWithClient(providerName);
        }
    }

    /**
     * Register an identity provider with the SPA client so it can be used for
     * federated login.
     */
    private void registerProviderWithClient(String providerName) {
        try {
            String clientId = cognitoProperties.getClientId();

            // Get current client configuration
            DescribeUserPoolClientResponse clientResponse = cognitoClient.describeUserPoolClient(
                    DescribeUserPoolClientRequest.builder()
                            .userPoolId(cognitoProperties.getUserPoolId())
                            .clientId(clientId)
                            .build());

            var client = clientResponse.userPoolClient();

            // Check if provider is already registered
            java.util.List<String> currentProviders = new java.util.ArrayList<>(
                    client.supportedIdentityProviders() != null
                            ? client.supportedIdentityProviders()
                            : java.util.List.of("COGNITO"));

            if (currentProviders.contains(providerName)) {
                log.debug("Provider {} already registered with client {}", providerName, clientId);
                return;
            }

            // Add the new provider
            currentProviders.add(providerName);

            // Update the client with the new provider
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
            log.error("Failed to register provider {} with client: {}", providerName, ex.getMessage(), ex);
            // Don't fail the overall operation, provider is still created
        }
    }

    private void deleteCognitoProvider(String providerName) {
        cognitoClient.deleteIdentityProvider(DeleteIdentityProviderRequest.builder()
                .userPoolId(cognitoProperties.getUserPoolId())
                .providerName(providerName)
                .build());
        log.info("Deleted Cognito identity provider: {}", providerName);
    }

    private Map<String, String> buildDefaultSamlAttributeMapping() {
        Map<String, String> mapping = new HashMap<>();
        mapping.put("email", "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress");
        mapping.put("name", "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name");
        // Note: custom:groups requires the attribute to be pre-created in Cognito
        // mapping.put("custom:groups",
        // "http://schemas.microsoft.com/ws/2008/06/identity/claims/groups");
        return mapping;
    }

    private Map<String, String> buildDefaultOidcAttributeMapping() {
        Map<String, String> mapping = new HashMap<>();
        mapping.put("email", "email");
        mapping.put("name", "name");
        mapping.put("username", "sub");
        // Note: custom:groups requires the attribute to be pre-created in Cognito
        // mapping.put("custom:groups", "groups");
        return mapping;
    }

    private Map<String, String> buildOidcProviderDetails(OidcConfigRequest request) {
        Map<String, String> details = new HashMap<>();
        details.put("client_id", request.clientId());
        details.put("client_secret", request.clientSecret());
        details.put("authorize_scopes", request.scopes() != null ? request.scopes() : "openid email profile");
        details.put("attributes_request_method", "GET");

        // Set issuer based on provider type
        switch (request.idpType()) {
            case GOOGLE -> details.put("oidc_issuer", "https://accounts.google.com");
            case AZURE_AD -> {
                // Azure requires tenant-specific issuer
                if (request.issuerUrl() != null) {
                    details.put("oidc_issuer", request.issuerUrl());
                }
                // user might give URL with v2.0 endpoint; for Azure OIDC, map groups attribute
                details.put("authorize_scopes", "openid email profile User.Read GroupMember.Read.All");
            }
            case OKTA, OIDC -> details.put("oidc_issuer", request.issuerUrl());
            default -> details.put("oidc_issuer", request.issuerUrl());
        }

        return details;
    }

    private IdentityProviderTypeType mapIdpTypeToProviderType(IdpType idpType) {
        return switch (idpType) {
            // Note: GOOGLE uses OIDC type because Cognito's GOOGLE type is reserved for
            // built-in social login
            case GOOGLE, AZURE_AD, OKTA, OIDC -> IdentityProviderTypeType.OIDC;
            case GOOGLE_SAML, SAML, PING -> IdentityProviderTypeType.SAML;
            default -> IdentityProviderTypeType.OIDC;
        };
    }

    private SsoConfigDto toSsoConfigDto(Tenant tenant) {
        @SuppressWarnings("unchecked")
        Map<String, Object> config = tenant.getIdpConfigJson();

        SsoConfigDto.SsoConfigDtoBuilder builder = SsoConfigDto.builder()
                .tenantId(tenant.getId())
                .ssoEnabled(Boolean.TRUE.equals(tenant.getSsoEnabled()))
                .idpType(tenant.getIdpType())
                .samlMetadataUrl(tenant.getIdpMetadataUrl())
                .samlEntityId(tenant.getIdpEntityId())
                .createdAt(tenant.getCreatedAt())
                .updatedAt(tenant.getUpdatedAt());

        if (config != null) {
            builder.providerName((String) config.get("providerName"));
            builder.cognitoProviderName((String) config.get("cognitoProviderName"));
            builder.jitProvisioningEnabled(Boolean.TRUE.equals(config.get("jitProvisioningEnabled")));
            builder.defaultRole((String) config.get("defaultRole"));
            builder.oidcIssuer((String) config.get("oidcIssuer"));
            builder.oidcClientId((String) config.get("oidcClientId"));
            builder.lastTestedAt((String) config.get("lastTestedAt"));
            builder.testStatus((String) config.get("testStatus"));

            @SuppressWarnings("unchecked")
            Map<String, String> attrMap = (Map<String, String>) config.get("attributeMappings");
            builder.attributeMappings(attrMap);
        }

        return builder.build();
    }

    private String getCognitoDomain() {
        // This should be fetched from SSM or cached from Terraform outputs
        // For now, return a placeholder that should be configured
        return System.getenv("COGNITO_DOMAIN");
    }

    private String getDefaultClientId() {
        return System.getenv("COGNITO_CLIENT_ID");
    }

    @Override
    public Optional<com.learning.platformservice.sso.controller.SsoConfigurationController.SsoLookupResponse> getSsoLookup(
            String tenantId) {
        Optional<Tenant> tenantOpt = tenantRepository.findById(tenantId);
        if (tenantOpt.isEmpty()) {
            return Optional.empty();
        }

        Tenant tenant = tenantOpt.get();
        if (!Boolean.TRUE.equals(tenant.getSsoEnabled()) || tenant.getIdpConfigJson() == null) {
            return Optional.empty();
        }

        String providerName = (String) tenant.getIdpConfigJson().get("cognitoProviderName");
        String idpType = tenant.getIdpType() != null ? tenant.getIdpType().name() : null;

        return Optional.of(new com.learning.platformservice.sso.controller.SsoConfigurationController.SsoLookupResponse(
                Boolean.TRUE.equals(tenant.getSsoEnabled()),
                providerName,
                idpType));
    }
}
