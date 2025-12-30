import {Injectable} from '@angular/core';
import {Observable} from 'rxjs';
import {BaseApiService} from '../../core/base-api.service';

/**
 * SSO Configuration DTO
 */
export interface SsoConfig {
    ssoEnabled: boolean;
    idpType: 'SAML' | 'OIDC' | 'OKTA' | 'AZURE_AD' | 'GOOGLE' | null;
    idpMetadataUrl: string | null;
    idpEntityId: string | null;
    oidcClientId: string | null;
    oidcIssuerUrl: string | null;
    configuredAt: string | null;
    lastTestedAt: string | null;
    testStatus: 'SUCCESS' | 'FAILED' | 'PENDING' | null;
}

/**
 * SAML configuration request
 */
export interface SamlConfigRequest {
    idpType: 'SAML' | 'OKTA';
    providerName: string;
    metadataUrl?: string;
    metadataXml?: string;
    entityId?: string;
    ssoUrl?: string;
}

/**
 * OIDC configuration request
 */
export interface OidcConfigRequest {
    idpType: 'OIDC' | 'AZURE_AD' | 'GOOGLE';
    providerName: string;
    clientId: string;
    clientSecret: string;
    issuerUrl?: string;
    scopes?: string;
}

/**
 * SSO test result
 */
export interface SsoTestResult {
    success: boolean;
    message: string;
    details: Record<string, string>;
}

/**
 * Service Provider metadata (for SAML)
 */
export interface SpMetadata {
    entityId: string;
    acsUrl: string;
    metadataXml: string;
}

/**
 * Available SSO provider options
 */
export const SSO_PROVIDERS = [
    { value: 'GOOGLE', label: 'Google Workspace', icon: 'pi pi-google', protocol: 'OIDC' },
    { value: 'AZURE_AD', label: 'Microsoft Azure AD', icon: 'pi pi-microsoft', protocol: 'OIDC' },
    { value: 'OKTA', label: 'Okta', icon: 'pi pi-shield', protocol: 'SAML' },
    { value: 'GENERIC_SAML', label: 'Generic SAML 2.0', icon: 'pi pi-key', protocol: 'SAML' },
    { value: 'GENERIC_OIDC', label: 'Generic OIDC', icon: 'pi pi-lock', protocol: 'OIDC' }
] as const;

/**
 * SSO Configuration Service
 *
 * Manages SSO configuration for tenant organizations.
 * Provides CRUD operations for SAML and OIDC identity providers.
 *
 * Features:
 * - Get current SSO configuration
 * - Configure SAML 2.0 providers (Okta, Ping, generic)
 * - Configure OIDC providers (Google, Azure AD, generic)
 * - Toggle SSO on/off
 * - Test SSO connection
 * - Get SP metadata for SAML setup
 */
@Injectable({
    providedIn: 'root'
})
export class SsoConfigService extends BaseApiService {

    constructor() {
        super('/api/v1/sso');
    }

    /**
     * Get current SSO configuration for the tenant
     */
    getConfiguration(): Observable<SsoConfig> {
        return this.http.get<SsoConfig>(`${this.resourceUrl}/config`);
    }

    /**
     * Save SAML configuration
     */
    saveSamlConfig(config: SamlConfigRequest): Observable<SsoConfig> {
        return this.http.post<SsoConfig>(`${this.resourceUrl}/config/saml`, config);
    }

    /**
     * Save OIDC configuration
     */
    saveOidcConfig(config: OidcConfigRequest): Observable<SsoConfig> {
        return this.http.post<SsoConfig>(`${this.resourceUrl}/config/oidc`, config);
    }

    /**
     * Toggle SSO enabled/disabled
     */
    toggleSso(enabled: boolean): Observable<SsoConfig> {
        return this.http.patch<SsoConfig>(`${this.resourceUrl}/toggle`, { enabled });
    }

    /**
     * Test SSO connection
     */
    testConnection(): Observable<SsoTestResult> {
        return this.http.post<SsoTestResult>(`${this.resourceUrl}/test`, {});
    }

    /**
     * Delete SSO configuration
     */
    deleteConfiguration(): Observable<void> {
        return this.http.delete<void>(`${this.resourceUrl}/config`);
    }

    /**
     * Get Service Provider metadata (for SAML setup)
     */
    getSpMetadata(): Observable<SpMetadata> {
        return this.http.get<SpMetadata>(`${this.resourceUrl}/sp-metadata`);
    }
}
