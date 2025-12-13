/**
 * Tenant information returned from the lookup API.
 * Used in the tenant selector during multi-tenant login.
 */
export interface TenantInfo {
    /** Unique tenant identifier */
    tenantId: string;

    /** Display name of the tenant */
    tenantName: string;

    /** Type of tenant: PERSONAL or ORGANIZATION */
    tenantType: 'PERSONAL' | 'ORGANIZATION';

    /** Company name (for organization tenants) */
    companyName?: string;

    /** URL to tenant's logo image */
    logoUrl?: string;

    /** Whether SSO is enabled for this tenant */
    ssoEnabled: boolean;

    /** SSO provider type (OKTA, AZURE_AD, etc.) if enabled */
    ssoProvider?: string;

    /** User's role hint in this tenant (owner, admin, member, guest) */
    roleHint: string;

    /** Whether the current user owns this tenant */
    isOwner: boolean;

    /** Whether this is the user's default workspace */
    isDefault: boolean;

    /** When the user last accessed this tenant */
    lastAccessedAt?: Date;
}

/**
 * Result of tenant lookup operation.
 */
export interface TenantLookupResult {
    /** Email address that was looked up */
    email: string;

    /** List of tenants the user belongs to */
    tenants: TenantInfo[];

    /** True if user has multiple tenants and must select one */
    requiresSelection: boolean;

    /** The default tenant ID to auto-select (if only one tenant) */
    defaultTenantId?: string;
}

/**
 * Input for login operation with tenant context.
 */
export interface LoginWithTenantInput {
    /** User's email address */
    email: string;

    /** User's password */
    password: string;

    /** Selected tenant ID for multi-tenant users */
    selectedTenantId: string;
}
