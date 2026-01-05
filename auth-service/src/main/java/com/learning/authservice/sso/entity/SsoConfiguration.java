package com.learning.authservice.sso.entity;

import com.learning.common.dto.IdpType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * SSO/IDP configuration for a tenant.
 * Stored in tenant-specific databases.
 */
@Entity
@Table(name = "sso_configurations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SsoConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Tenant ID this config belongs to.
     * Used for validation and lookup.
     */
    @Column(name = "tenant_id", nullable = false, unique = true)
    private String tenantId;

    @Column(name = "sso_enabled")
    @Builder.Default
    private Boolean ssoEnabled = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "idp_type")
    private IdpType idpType;

    @Column(name = "provider_name")
    private String providerName;

    // SAML Configuration
    @Column(name = "saml_metadata_url", length = 1024)
    private String samlMetadataUrl;

    @Column(name = "saml_metadata_xml", columnDefinition = "TEXT")
    private String samlMetadataXml;

    @Column(name = "saml_entity_id")
    private String samlEntityId;

    @Column(name = "saml_sso_url", length = 1024)
    private String samlSsoUrl;

    @Column(name = "saml_certificate", columnDefinition = "TEXT")
    private String samlCertificate;

    // OIDC Configuration
    @Column(name = "oidc_issuer", length = 1024)
    private String oidcIssuer;

    @Column(name = "oidc_client_id")
    private String oidcClientId;

    @Column(name = "oidc_client_secret")
    private String oidcClientSecret;

    @Column(name = "oidc_scopes")
    @Builder.Default
    private String oidcScopes = "openid email profile";

    // Attribute mappings: IdP attr -> Cognito attr
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attribute_mappings", columnDefinition = "jsonb")
    private Map<String, String> attributeMappings;

    // JIT Provisioning
    @Column(name = "jit_provisioning_enabled")
    @Builder.Default
    private Boolean jitProvisioningEnabled = false;

    @Column(name = "default_role")
    private String defaultRole;

    // Cognito Identity Provider name (auto-generated)
    @Column(name = "cognito_provider_name")
    private String cognitoProviderName;

    // Test status
    @Column(name = "last_tested_at")
    private OffsetDateTime lastTestedAt;

    @Column(name = "test_status")
    private String testStatus;

    // Audit fields
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
