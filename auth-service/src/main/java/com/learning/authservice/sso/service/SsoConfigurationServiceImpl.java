package com.learning.authservice.sso.service;

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
import software.amazon.awssdk.services.cognitoidentityprovider.model.DescribeIdentityProviderResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.IdentityProviderType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.IdentityProviderTypeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ResourceNotFoundException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

/**
 * Implementation of SSO configuration service for auth-service.
 * 
 * Uses extracted helper classes for better Single Responsibility:
 * - CognitoProviderManager: Handles AWS Cognito SDK operations
 * - SsoAttributeMappingBuilder: Handles attribute mapping configuration
 * 
 * This class focuses on business logic and orchestration.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SsoConfigurationServiceImpl implements SsoConfigurationService {

    private final SsoConfigurationRepository ssoConfigurationRepository;
    private final CognitoProviderManager cognitoProviderManager;
    private final SsoAttributeMappingBuilder attributeMappingBuilder;

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
        String providerName = cognitoProviderManager.buildProviderName(tenantId, request.idpType());

        // Prepare provider details for SAML using helper
        Map<String, String> providerDetails = attributeMappingBuilder.buildSamlProviderDetails(
                request.metadataUrl(), request.metadataXml());

        // Prepare attribute mapping using helper
        Map<String, String> attributeMapping = attributeMappingBuilder.buildSamlAttributeMapping(
                request.attributeMappings());

        // Create or update Cognito Identity Provider using manager
        cognitoProviderManager.createOrUpdateProvider(providerName, IdentityProviderTypeType.SAML,
                providerDetails, attributeMapping);

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

        // Build provider name using helper
        String providerName = cognitoProviderManager.buildProviderName(tenantId, request.idpType());

        // Determine provider type and details using helper
        IdentityProviderTypeType providerType = cognitoProviderManager.mapIdpTypeToProviderType(request.idpType());
        Map<String, String> providerDetails = attributeMappingBuilder.buildOidcProviderDetails(request);

        // Prepare attribute mapping using helper
        Map<String, String> attributeMapping = attributeMappingBuilder.buildOidcAttributeMapping(
                request.attributeMappings());

        // Create or update Cognito Identity Provider using manager
        cognitoProviderManager.createOrUpdateProvider(providerName, providerType, providerDetails, attributeMapping);

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

        // Delete Cognito Identity Provider using manager
        if (config.getCognitoProviderName() != null) {
            cognitoProviderManager.deleteProvider(config.getCognitoProviderName());
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

            // Describe the provider to verify it exists using manager
            DescribeIdentityProviderResponse response = cognitoProviderManager.describeProvider(
                    config.getCognitoProviderName());

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
        return String.format("""
                SAML Service Provider Metadata
                ==============================
                Entity ID: urn:amazon:cognito:sp:%s
                ACS URL: https://%s.auth.%s.amazoncognito.com/saml2/idpresponse

                Use these values to configure your Identity Provider.
                """,
                cognitoProviderManager.getUserPoolId(),
                cognitoProviderManager.getCognitoDomain(),
                "us-east-1"); // TODO: Get region from config
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

    // ========================================================================
    // Entity mapping
    // ========================================================================

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
}
