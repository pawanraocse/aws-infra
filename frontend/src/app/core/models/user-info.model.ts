/**
 * Authenticated user information from JWT token.
 */
export interface UserInfo {
    /** Cognito user ID (sub) */
    userId: string;

    /** User's email address */
    email: string;

    /** Current tenant ID the user is authenticated with */
    tenantId: string;

    /** User's role within the current tenant */
    role: string;

    /** Type of tenant: PERSONAL or ORGANIZATION */
    tenantType: 'PERSONAL' | 'ORGANIZATION';

    /** Whether the user's email is verified */
    emailVerified?: boolean;
}
