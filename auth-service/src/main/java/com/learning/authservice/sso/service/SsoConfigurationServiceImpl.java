package com.learning.authservice.sso.service;

import com.learning.authservice.config.CognitoProperties;
import com.learning.authservice.sso.dto.OidcConfigRequest;
import com.learning.authservice.sso.dto.SamlConfigRequest;
import com.learning.authservice.sso.dto.SsoConfigDto;
import com.learning.authservice.sso.dto.SsoTestResult;
import com.learning.authservice.sso.entity.SsoConfiguration;
import com.learning.authservice.sso.exception.SsoConfigurationException;
import com.learning.authservice.sso.repository.SsoConfigurationRepository;
import com.learning.common.dto.IdpType;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implementation of SSO configuration service for auth-service.
 * Uses AWS Cognito SDK to manage identity providers.
 * Stores SSO config in tenant-specific databases via SsoConfiguration entity.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SsoConfigurationServiceImpl implements SsoConfigurationService {

    private final SsoConfigurationRepository ssoConfigurationRepository;
    private final CognitoIdentityProviderClient cognitoClient;
    private final CognitoProperties cognitoProperties;

    @Override
    public Optional<SsoConfigDto> getConfiguration(String tenantId) {
        return ssoConfigurationRepository.findByTenantId(tenantId)
                .filter(config -> config.getIdpType() != null)
                .map(this::toSsoConfigDto);
    }

    @Override
    @Transactional
    public SsoConfigDto saveSamlConfiguration(String tenantId, SamlConfigRequest request) {
        log.info("Configuring SAML SSO for tenant: {}, provider: {}", tenantId, request.providerName());

        SsoConfiguration config = getOrCreateConfig(tenantId);

        // Validate request
        validateSamlRequest(request);

        // Build Cognito Identity Provider name
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

        // Update SSO configuration
        config.setIdpType(request.idpType());
        config.setProviderName(request.providerName());
        config.setSamlMetadataUrl(request.metadataUrl());
        config.setSamlMetadataXml(request.metadataXml());
        config.setSamlEntityId(request.entityId());
        config.setSamlSsoUrl(request.ssoUrl());
        config.setSamlCertificate(request.certificate());
        config.setSsoEnabled(true);
        config.setCognitoProviderName(providerName);
        config.setJitProvisioningEnabled(request.jitProvisioningEnabled());
        config.setDefaultRole(request.defaultRole() != null ? request.defaultRole() : "viewer");
        config.setAttributeMappings(attributeMapping);

        ssoConfigurationRepository.save(config);

        log.info("SAML SSO configured successfully for tenant: {}", tenantId);
        return toSsoConfigDto(config);
    }

    @Override
    @Transactional
    public SsoConfigDto saveOidcConfiguration(String tenantId, OidcConfigRequest request) {
        log.info("Configuring OIDC SSO for tenant: {}, provider: {}", tenantId, request.providerName());

        SsoConfiguration config = getOrCreateConfig(tenantId);

        // Validate request
        validateOidcRequest(request);

        // Build provider name
        String providerName = buildProviderName(tenantId, request.idpType());

        // Determine provider type and details
        IdentityProviderTypeType providerType = mapIdpTypeToProviderType(request.idpType());
        Map<String, String> providerDetails = buildOidcProviderDetails(request);

        // Prepare attribute mapping
        Map<String, String> attributeMapping = buildDefaultOidcAttributeMapping();
        if (request.attributeMappings() != null) {
            attributeMapping.putAll(request.attributeMappings());
        }

        // Create or update Cognito Identity Provider
        createOrUpdateCognitoProvider(providerName, providerType, providerDetails, attributeMapping);

        // Update SSO configuration
        config.setIdpType(request.idpType());
        config.setProviderName(request.providerName());
        config.setOidcIssuer(request.issuerUrl());
        config.setOidcClientId(request.clientId());
        config.setOidcClientSecret(request.clientSecret());
        config.setOidcScopes(request.scopes() != null ? request.scopes() : "openid email profile");
        config.setSsoEnabled(true);
        config.setCognitoProviderName(providerName);
        config.setJitProvisioningEnabled(request.jitProvisioningEnabled());
        config.setDefaultRole(request.defaultRole() != null ? request.defaultRole() : "viewer");
        config.setAttributeMappings(attributeMapping);

        ssoConfigurationRepository.save(config);

        log.info("OIDC SSO configured successfully for tenant: {}", tenantId);
        return toSsoConfigDto(config);
    }

    @Override
    @Transactional
    public SsoConfigDto toggleSso(String tenantId, boolean enabled) {
        SsoConfiguration config = ssoConfigurationRepository.findByTenantId(tenantId)
                .orElseThrow(() -> SsoConfigurationException.notConfigured(tenantId));

        config.setSsoEnabled(enabled);
        ssoConfigurationRepository.save(config);

        log.info("SSO {} for tenant: {}", enabled ? "enabled" : "disabled", tenantId);
        return toSsoConfigDto(config);
    }

    @Override
    @Transactional
    public void deleteConfiguration(String tenantId) {
        Optional<SsoConfiguration> configOpt = ssoConfigurationRepository.findByTenantId(tenantId);
        if (configOpt.isEmpty()) {
            return;
        }

        SsoConfiguration config = configOpt.get();

        // Delete Cognito Identity Provider
        if (config.getCognitoProviderName() != null) {
            try {
                deleteCognitoProvider(config.getCognitoProviderName());
            } catch (ResourceNotFoundException e) {
                log.warn("Cognito provider not found: {}", config.getCognitoProviderName());
            }
        }

        ssoConfigurationRepository.delete(config);
        log.info("SSO configuration deleted for tenant: {}", tenantId);
    }

    @Override
    public SsoTestResult testConnection(String tenantId) {
        long startTime = System.currentTimeMillis();

        try {
            Optional<SsoConfiguration> configOpt = ssoConfigurationRepository.findByTenantId(tenantId);
            if (configOpt.isEmpty()) {
                return SsoTestResult.builder()
                        .success(false)
                        .message("SSO not configured for this tenant")
                        .responseTimeMs(System.currentTimeMillis() - startTime)
                        .build();
            }

            SsoConfiguration config = configOpt.get();

            // Describe the provider to verify it exists
            DescribeIdentityProviderResponse response = cognitoClient.describeIdentityProvider(
                    DescribeIdentityProviderRequest.builder()
                            .userPoolId(cognitoProperties.getUserPoolId())
                            .providerName(config.getCognitoProviderName())
                            .build());

            IdentityProviderType provider = response.identityProvider();

            // Update test status
            config.setLastTestedAt(OffsetDateTime.now());
            config.setTestStatus("SUCCESS");
            ssoConfigurationRepository.save(config);

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

    @Override
    public String getSpMetadata(String tenantId) {
        String domain = getCognitoDomain();
        return String.format("""
                SAML Service Provider Metadata

                ACS URL: https://%s.auth.%s.amazoncognito.com/saml2/idpresponse
                Entity ID: urn:amazon:cognito:sp:%s
                Metadata URL: https://%s.auth.%s.amazoncognito.com/saml2/idpmetadata
                """, domain, cognitoProperties.getRegion(), cognitoProperties.getUserPoolId(),
                domain, cognitoProperties.getRegion());
    }

    @Override
    public Optional<SsoLookupResponse> getSsoLookup(String tenantId) {
        // Set tenant context to route query to correct tenant database
        // This is a public endpoint, so we set context from the request parameter
        com.learning.common.infra.tenant.TenantContext.setCurrentTenant(tenantId);
        try {
            return ssoConfigurationRepository.findByTenantId(tenantId)
                    .filter(config -> Boolean.TRUE.equals(config.getSsoEnabled()))
                    .map(config -> new SsoLookupResponse(
                            true,
                            config.getCognitoProviderName(),
                            config.getIdpType() != null ? config.getIdpType().name() : null));
        } finally {
            com.learning.common.infra.tenant.TenantContext.clear();
        }
    }

    // ========== Private Helpers ==========

    private SsoConfiguration getOrCreateConfig(String tenantId) {
        return ssoConfigurationRepository.findByTenantId(tenantId)
                .orElseGet(() -> SsoConfiguration.builder()
                        .tenantId(tenantId)
                        .ssoEnabled(false)
                        .build());
    }

    private void updateTestStatus(String tenantId, String status) {
        ssoConfigurationRepository.findByTenantId(tenantId).ifPresent(config -> {
            config.setLastTestedAt(OffsetDateTime.now());
            config.setTestStatus(status);
            ssoConfigurationRepository.save(config);
        });
    }

    private void validateSamlRequest(SamlConfigRequest request) {
        if (request.idpType() == null) {
            throw SsoConfigurationException.invalidConfiguration("IdP type is required");
        }
        boolean hasMetadata = (request.metadataUrl() != null && !request.metadataUrl().isBlank())
                || (request.metadataXml() != null && !request.metadataXml().isBlank());
        boolean hasManualConfig = request.entityId() != null && request.ssoUrl() != null;
        if (!hasMetadata && !hasManualConfig) {
            throw SsoConfigurationException.invalidConfiguration(
                    "Either metadata URL/XML or entity ID + SSO URL must be provided");
        }
    }

    private void validateOidcRequest(OidcConfigRequest request) {
        if (request.idpType() == null) {
            throw SsoConfigurationException.invalidConfiguration("IdP type is required");
        }
        if (request.clientId() == null || request.clientId().isBlank()) {
            throw SsoConfigurationException.invalidConfiguration("Client ID is required");
        }
        if (request.clientSecret() == null || request.clientSecret().isBlank()) {
            throw SsoConfigurationException.invalidConfiguration("Client Secret is required");
        }
        // Generic OIDC requires issuer URL
        if (request.idpType() == IdpType.OIDC &&
                (request.issuerUrl() == null || request.issuerUrl().isBlank())) {
            throw SsoConfigurationException.invalidConfiguration("Issuer URL is required for generic OIDC");
        }
    }

    private String buildProviderName(String tenantId, IdpType idpType) {
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

    private void createOrUpdateCognitoProvider(String providerName, IdentityProviderTypeType providerType,
            Map<String, String> providerDetails, Map<String, String> attributeMapping) {
        try {
            cognitoClient.describeIdentityProvider(DescribeIdentityProviderRequest.builder()
                    .userPoolId(cognitoProperties.getUserPoolId())
                    .providerName(providerName)
                    .build());

            cognitoClient.updateIdentityProvider(UpdateIdentityProviderRequest.builder()
                    .userPoolId(cognitoProperties.getUserPoolId())
                    .providerName(providerName)
                    .providerDetails(providerDetails)
                    .attributeMapping(attributeMapping)
                    .build());

            log.info("Updated Cognito identity provider: {}", providerName);

        } catch (ResourceNotFoundException e) {
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
        mapping.put("custom:samlGroups", "group");
        return mapping;
    }

    private Map<String, String> buildDefaultOidcAttributeMapping() {
        Map<String, String> mapping = new HashMap<>();
        mapping.put("email", "email");
        mapping.put("name", "name");
        mapping.put("username", "sub");
        return mapping;
    }

    private Map<String, String> buildOidcProviderDetails(OidcConfigRequest request) {
        Map<String, String> details = new HashMap<>();
        details.put("client_id", request.clientId());
        details.put("client_secret", request.clientSecret());
        details.put("authorize_scopes", request.scopes() != null ? request.scopes() : "openid email profile");
        details.put("attributes_request_method", "GET");

        switch (request.idpType()) {
            case GOOGLE -> details.put("oidc_issuer", "https://accounts.google.com");
            case AZURE_AD -> {
                if (request.issuerUrl() != null) {
                    details.put("oidc_issuer", request.issuerUrl());
                }
                details.put("authorize_scopes", "openid email profile User.Read GroupMember.Read.All");
            }
            default -> details.put("oidc_issuer", request.issuerUrl());
        }

        return details;
    }

    private IdentityProviderTypeType mapIdpTypeToProviderType(IdpType idpType) {
        return switch (idpType) {
            case GOOGLE, AZURE_AD, OKTA, OIDC -> IdentityProviderTypeType.OIDC;
            case GOOGLE_SAML, SAML, PING -> IdentityProviderTypeType.SAML;
            default -> IdentityProviderTypeType.OIDC;
        };
    }

    private SsoConfigDto toSsoConfigDto(SsoConfiguration config) {
        return SsoConfigDto.builder()
                .tenantId(config.getTenantId())
                .ssoEnabled(Boolean.TRUE.equals(config.getSsoEnabled()))
                .idpType(config.getIdpType())
                .providerName(config.getProviderName())
                .samlMetadataUrl(config.getSamlMetadataUrl())
                .samlMetadataXml(config.getSamlMetadataXml())
                .samlEntityId(config.getSamlEntityId())
                .samlSsoUrl(config.getSamlSsoUrl())
                .samlCertificate(config.getSamlCertificate())
                .oidcIssuer(config.getOidcIssuer())
                .oidcClientId(config.getOidcClientId())
                .oidcScopes(config.getOidcScopes())
                .attributeMappings(config.getAttributeMappings())
                .jitProvisioningEnabled(Boolean.TRUE.equals(config.getJitProvisioningEnabled()))
                .defaultRole(config.getDefaultRole())
                .cognitoProviderName(config.getCognitoProviderName())
                .lastTestedAt(config.getLastTestedAt() != null ? config.getLastTestedAt().toString() : null)
                .testStatus(config.getTestStatus())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .build();
    }

    private String getCognitoDomain() {
        return cognitoProperties.getDomain();
    }
}
