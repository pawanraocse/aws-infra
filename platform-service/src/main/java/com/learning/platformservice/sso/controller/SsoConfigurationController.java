package com.learning.platformservice.sso.controller;

import com.learning.common.infra.security.RequirePermission;
import com.learning.platformservice.sso.dto.OidcConfigRequest;
import com.learning.platformservice.sso.dto.SamlConfigRequest;
import com.learning.platformservice.sso.dto.SsoConfigDto;
import com.learning.platformservice.sso.dto.SsoTestResult;
import com.learning.platformservice.sso.service.SsoConfigurationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for SSO configuration management.
 * Allows tenant admins to configure SAML/OIDC identity providers.
 */
@RestController
@RequestMapping("/api/v1/sso")
@RequiredArgsConstructor
@Slf4j
public class SsoConfigurationController {

    private final SsoConfigurationService ssoConfigurationService;

    /**
     * Get current SSO configuration for the tenant.
     */
    @GetMapping("/config")
    @RequirePermission(resource = "sso", action = "read")
    public ResponseEntity<SsoConfigDto> getConfiguration(
            @RequestHeader("X-Tenant-Id") String tenantId) {
        log.info("Getting SSO configuration for tenant: {}", tenantId);

        return ssoConfigurationService.getConfiguration(tenantId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    /**
     * Configure SAML identity provider.
     */
    @PostMapping("/config/saml")
    @RequirePermission(resource = "sso", action = "manage")
    public ResponseEntity<SsoConfigDto> configureSaml(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @Valid @RequestBody SamlConfigRequest request) {
        log.info("Configuring SAML SSO for tenant: {}", tenantId);

        SsoConfigDto config = ssoConfigurationService.saveSamlConfiguration(tenantId, request);
        return ResponseEntity.ok(config);
    }

    /**
     * Configure OIDC identity provider (Google, Azure AD, Okta, etc.).
     */
    @PostMapping("/config/oidc")
    @RequirePermission(resource = "sso", action = "manage")
    public ResponseEntity<SsoConfigDto> configureOidc(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @Valid @RequestBody OidcConfigRequest request) {
        log.info("Configuring OIDC SSO for tenant: {}", tenantId);

        SsoConfigDto config = ssoConfigurationService.saveOidcConfiguration(tenantId, request);
        return ResponseEntity.ok(config);
    }

    /**
     * Enable or disable SSO for the tenant.
     */
    @PatchMapping("/config/toggle")
    @RequirePermission(resource = "sso", action = "manage")
    public ResponseEntity<SsoConfigDto> toggleSso(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestParam boolean enabled) {
        log.info("Toggling SSO for tenant: {} to: {}", tenantId, enabled);

        SsoConfigDto config = ssoConfigurationService.toggleSso(tenantId, enabled);
        return ResponseEntity.ok(config);
    }

    /**
     * Delete SSO configuration and remove identity provider from Cognito.
     */
    @DeleteMapping("/config")
    @RequirePermission(resource = "sso", action = "manage")
    public ResponseEntity<Void> deleteConfiguration(
            @RequestHeader("X-Tenant-Id") String tenantId) {
        log.info("Deleting SSO configuration for tenant: {}", tenantId);

        ssoConfigurationService.deleteConfiguration(tenantId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Test SSO connection by validating the identity provider configuration.
     */
    @PostMapping("/test")
    @RequirePermission(resource = "sso", action = "manage")
    public ResponseEntity<SsoTestResult> testConnection(
            @RequestHeader("X-Tenant-Id") String tenantId) {
        log.info("Testing SSO connection for tenant: {}", tenantId);

        SsoTestResult result = ssoConfigurationService.testConnection(tenantId);
        return ResponseEntity.ok(result);
    }

    /**
     * Get SAML Service Provider (SP) metadata.
     * This endpoint is public so IdP admins can access it.
     */
    @GetMapping("/sp-metadata")
    public ResponseEntity<String> getSpMetadata(
            @RequestParam String tenantId) {
        log.info("Getting SP metadata for tenant: {}", tenantId);

        String metadata = ssoConfigurationService.getSpMetadata(tenantId);
        return ResponseEntity.ok()
                .header("Content-Type", "application/xml")
                .body(metadata);
    }

    /**
     * Public endpoint to lookup SSO provider info for a tenant.
     * Used by the login page to redirect to the correct identity provider.
     * Returns minimal info: ssoEnabled and cognitoProviderName.
     */
    @GetMapping("/lookup")
    public ResponseEntity<SsoLookupResponse> lookupSso(
            @RequestParam String tenantId) {
        log.info("Looking up SSO for tenant: {}", tenantId);

        return ssoConfigurationService.getSsoLookup(tenantId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * DTO for SSO lookup response (minimal info for login redirect).
     */
    public record SsoLookupResponse(
            boolean ssoEnabled,
            String cognitoProviderName,
            String idpType) {
    }
}
