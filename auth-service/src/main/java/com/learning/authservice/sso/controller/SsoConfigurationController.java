package com.learning.authservice.sso.controller;

import com.learning.authservice.sso.dto.OidcConfigRequest;
import com.learning.authservice.sso.dto.SamlConfigRequest;
import com.learning.authservice.sso.dto.SsoConfigDto;
import com.learning.authservice.sso.dto.SsoTestResult;
import com.learning.authservice.sso.service.SsoConfigurationService;
import com.learning.common.infra.security.RequirePermission;
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
     * Accepts tenantId as query param (for public access) OR from header (for
     * authenticated calls).
     */
    @GetMapping("/sp-metadata")
    public ResponseEntity<String> getSpMetadata(
            @RequestParam(required = false) String tenantId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String headerTenantId) {

        String resolvedTenantId = tenantId != null ? tenantId : headerTenantId;
        if (resolvedTenantId == null || resolvedTenantId.isBlank()) {
            return ResponseEntity.badRequest().body("tenantId is required (query param or X-Tenant-Id header)");
        }

        log.info("Getting SP metadata for tenant: {}", resolvedTenantId);

        String metadata = ssoConfigurationService.getSpMetadata(resolvedTenantId);
        return ResponseEntity.ok()
                .header("Content-Type", "application/xml")
                .body(metadata);
    }

    /**
     * Public endpoint to lookup SSO provider info for a tenant.
     * Used by the login page to redirect to the correct identity provider.
     */
    @GetMapping("/lookup")
    public ResponseEntity<SsoConfigurationService.SsoLookupResponse> lookupSso(
            @RequestParam String tenantId) {
        log.info("Looking up SSO for tenant: {}", tenantId);

        return ssoConfigurationService.getSsoLookup(tenantId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
