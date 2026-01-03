import {inject, Injectable, signal} from '@angular/core';
import {fetchAuthSession, getCurrentUser, signIn, SignInOutput, signOut} from 'aws-amplify/auth';
import {Router} from '@angular/router';
import {HttpClient} from '@angular/common/http';
import {firstValueFrom} from 'rxjs';

import {
  AuthExceptionType,
  classifyAuthError,
  getAuthErrorMessage,
  LoginWithTenantInput,
  TenantInfo,
  TenantLookupResult,
  UserInfo
} from './models';
import {environment} from '../../environments/environment';

/**
 * Authentication service with multi-tenant support.
 *
 * Handles:
 * - Email-first tenant lookup
 * - Multi-tenant login with tenant selection
 * - Session management and user info extraction
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly router = inject(Router);
  private readonly http = inject(HttpClient);

  /** Current authenticated user */
  readonly user = signal<UserInfo | null>(null);

  /** Authentication state */
  readonly isAuthenticated = signal<boolean>(false);

  /** Currently selected tenant for multi-tenant login */
  private selectedTenantId: string | null = null;

  constructor() {
    this.checkAuth();
  }

  // ========== Multi-Tenant Login Flow ==========

  /**
   * Lookup tenants for a given email address.
   * First step of the multi-tenant login flow.
   *
   * @param email User's email address
   * @returns TenantLookupResult with tenants and flow control info
   */
  async lookupTenants(email: string): Promise<TenantLookupResult> {
    try {
      const result = await firstValueFrom(
        this.http.get<TenantLookupResult>(
          `${environment.apiUrl}/auth/api/v1/auth/lookup`,
          { params: { email } }
        )
      );

      // Transform dates from strings
      if (result.tenants) {
        result.tenants = result.tenants.map(t => ({
          ...t,
          lastAccessedAt: t.lastAccessedAt ? new Date(t.lastAccessedAt) : undefined
        }));
      }

      return result;
    } catch (error) {
      console.error('Tenant lookup failed', error);
      // Return empty result on error
      return {
        email,
        tenants: [],
        requiresSelection: false,
        defaultTenantId: undefined
      };
    }
  }

  /**
   * Login with a selected tenant context.
   * Main login method for multi-tenant flow.
   *
   * @param input Login credentials with selected tenant
   * @returns SignInOutput from Cognito
   * @throws Error with classified exception type
   */
  async loginWithTenant(input: LoginWithTenantInput): Promise<SignInOutput> {
    try {
      // Store selected tenant for use in login
      this.selectedTenantId = input.selectedTenantId;

      // Call Cognito signIn with clientMetadata containing selectedTenantId
      // This is passed to the PreTokenGeneration Lambda
      const result = await signIn({
        username: input.email,
        password: input.password,
        options: {
          clientMetadata: {
            selectedTenantId: input.selectedTenantId
          }
        }
      });

      if (result.isSignedIn) {
        await this.checkAuth();
        await this.updateLastAccessed(input.email, input.selectedTenantId);
        this.router.navigate(['/app']);
      }

      return result;
    } catch (error) {
      const errorType = classifyAuthError(error);
      const message = getAuthErrorMessage(errorType);
      console.error('Login failed:', errorType, message);

      // Re-throw with enhanced error
      const enhancedError = new Error(message);
      (enhancedError as unknown as { code: AuthExceptionType }).code = errorType;
      throw enhancedError;
    }
  }

  /**
   * Initiate SSO login by redirecting to Cognito Hosted UI with identity provider.
   * This triggers federated authentication via SAML/OIDC.
   * Uses Amplify's signInWithRedirect for proper OAuth code exchange.
   *
   * @param tenant TenantInfo with SSO configuration
   */
  async loginWithSSO(tenant: TenantInfo): Promise<void> {
    // The identity provider name should match what's configured in Cognito
    const identityProvider = tenant.cognitoProviderName || `OKTA-${tenant.tenantId}`;

    // Store tenant ID for post-login processing
    sessionStorage.setItem('sso_pending_tenant', tenant.tenantId);

    console.log('[Auth] Initiating SSO with provider:', identityProvider);

    try {
      // Use Amplify's signInWithRedirect for proper OAuth flow
      // This handles the code exchange automatically on callback
      const { signInWithRedirect } = await import('aws-amplify/auth');
      await signInWithRedirect({
        provider: {
          custom: identityProvider
        }
      });
    } catch (error) {
      console.error('[Auth] SSO redirect failed:', error);
      // Fallback to manual URL if signInWithRedirect fails
      this.loginWithSSOManual(tenant);
    }
  }

  /**
   * Fallback: Manual SSO URL building if Amplify redirect fails.
   */
  private loginWithSSOManual(tenant: TenantInfo): void {
    const cognitoDomain = environment.cognito.domain;
    const clientId = environment.cognito.userPoolWebClientId;
    const redirectUri = encodeURIComponent(`${window.location.origin}/auth/callback`);
    const identityProvider = tenant.cognitoProviderName || `OKTA-${tenant.tenantId}`;

    const ssoUrl = `https://${cognitoDomain}/oauth2/authorize` +
      `?identity_provider=${encodeURIComponent(identityProvider)}` +
      `&response_type=code` +
      `&client_id=${clientId}` +
      `&redirect_uri=${redirectUri}` +
      `&scope=openid+email+profile`;

    console.log('[Auth] Fallback SSO URL:', ssoUrl);
    window.location.href = ssoUrl;
  }

  /**
   * Login with a social identity provider (Google, Facebook, etc.).
   * Uses Cognito's built-in social providers for personal sign-in (B2C).
   * This is separate from organization SSO which uses tenant-specific providers.
   *
   * @param provider Social provider name (e.g., 'Google')
   */
  async loginWithSocialProvider(provider: string): Promise<void> {
    console.log(`[Auth] Initiating ${provider} social login via Amplify`);

    try {
      // Use Amplify's signInWithRedirect for proper OAuth handling
      // Amplify will handle the code exchange on callback
      const { signInWithRedirect } = await import('aws-amplify/auth');
      await signInWithRedirect({
        provider: { custom: provider }
      });
    } catch (err) {
      console.error(`[Auth] ${provider} login failed:`, err);
      // Fallback to direct URL if Amplify fails
      const cognitoDomain = environment.cognito.domain;
      const clientId = environment.cognito.userPoolWebClientId;
      const redirectUri = encodeURIComponent(`${window.location.origin}/auth/callback`);

      const socialUrl = `https://${cognitoDomain}/oauth2/authorize` +
        `?identity_provider=${encodeURIComponent(provider)}` +
        `&response_type=code` +
        `&client_id=${clientId}` +
        `&redirect_uri=${redirectUri}` +
        `&scope=openid+email+profile`;

      console.log(`[Auth] Fallback: Redirecting to ${provider} social login:`, socialUrl);
      window.location.href = socialUrl;
    }
  }

  /**
   * JIT provision an SSO user after successful login.
   * Called from callback component after SSO auth completes.
   * This is needed because Lambda can't reach local services during development.
   *
   * Note: Role assignment is handled via group mappings configured in the admin UI.
   * The Lambda's _resolve_role_from_groups function maps IdP groups to roles.
   */
  async jitProvisionSsoUser(tenantId: string, email: string, cognitoUserId: string): Promise<boolean> {
    try {
      console.log('[Auth] JIT provisioning SSO user:', { tenantId, email, cognitoUserId });

      // Call auth-service sso-complete endpoint (unified signup pipeline)
      const jitResponse = await firstValueFrom(
        this.http.post<{ success: boolean; message: string; tenantId?: string }>(
          `${environment.apiUrl}/auth-service/api/v1/auth/sso-complete`,
          {
            tenantId,
            email,
            cognitoUserId,
            source: 'SAML_SSO',
            defaultRole: 'viewer', // SSO users get least privilege, group mapping can elevate
            groups: []
          }
        )
      );

      console.log('[Auth] JIT provision result:', jitResponse);
      return jitResponse.success;
    } catch (error: any) {
      // 409 Conflict, duplicate membership, or similar means user already exists, which is fine
      if (error.status === 409 ||
        error.status === 400 && error.error?.message?.includes('Membership') ||
        error.error?.message?.includes('already exists')) {
        console.log('[Auth] User already provisioned (ignoring duplicate)');
        return true;
      }
      console.warn('[Auth] JIT provision failed:', error);
      return false;
    }
  }

  /**
   * Ensure personal tenant exists for social login users.
   * Creates a PERSONAL type tenant if it doesn't exist.
   * This is called for users who sign in with Google/Facebook/etc.
   *
   * @param tenantId Personal tenant ID (e.g., 'personal-pawanweblink')
   * @param email User's email address
   * @param cognitoUserId Cognito user sub
   */
  async ensurePersonalTenantExists(tenantId: string, email: string, cognitoUserId: string): Promise<boolean> {
    if (!tenantId.startsWith('personal-')) {
      // Not a personal tenant, skip creation
      return true;
    }

    try {
      console.log('[Auth] Ensuring personal tenant exists:', { tenantId, email });

      // Call auth-service sso-complete endpoint (unified signup pipeline)
      // This creates the personal tenant, membership, and roles in one call
      const response = await firstValueFrom(
        this.http.post<{ success: boolean; message: string; tenantId?: string }>(
          `${environment.apiUrl}/auth-service/api/v1/auth/sso-complete`,
          {
            tenantId,
            email,
            cognitoUserId,
            source: 'GOOGLE', // Personal Google sign-in
            defaultRole: 'admin', // Personal account owners are admin
            groups: []
          }
        )
      );

      console.log('[Auth] Personal tenant creation result:', response);
      return response.success;
    } catch (error: any) {
      // 409 = tenant already exists, 200/201 with already exists message
      if (error.status === 409 || error.error?.message?.includes('already exists')) {
        console.log('[Auth] Personal tenant already exists');
        return true;
      }
      console.error('[Auth] Failed to create personal tenant:', error);
      return false;
    }
  }

  /**
   * Update last accessed timestamp after successful login.
   * Call this after authentication completes.
   */
  private async updateLastAccessed(email: string, tenantId: string): Promise<void> {
    try {
      await firstValueFrom(
        this.http.patch(
          `${environment.apiUrl}/auth/api/v1/auth/last-accessed`,
          null,
          { params: { email, tenantId } }
        )
      );
    } catch (error) {
      // Non-critical, just log
      console.warn('Failed to update last accessed', error);
    }
  }

  // ========== Legacy Login (kept for backward compatibility) ==========

  /**
   * Sign in with username and password (legacy method).
   * @deprecated Use loginWithTenant for multi-tenant flows
   */
  async login(email: string, password: string): Promise<SignInOutput> {
    try {
      const result = await signIn({ username: email, password });
      if (result.isSignedIn) {
        await this.checkAuth();
        this.router.navigate(['/app']);
      }
      return result;
    } catch (error) {
      console.error('Login failed', error);
      throw error;
    }
  }

  /**
   * Check current authentication state and load user info.
   * Role is fetched from auth-service API (single source of truth).
   */
  async checkAuth(): Promise<boolean> {
    try {
      const currentUser = await getCurrentUser();
      const session = await fetchAuthSession();
      const idToken = session.tokens?.idToken?.payload;
      const idTokenString = session.tokens?.idToken?.toString();

      if (idToken) {
        const tenantId = (idToken['custom:tenantId'] as string) || '';

        // Lookup tenantType from platform-service (single source of truth)
        let tenantType: 'PERSONAL' | 'ORGANIZATION' = 'PERSONAL';
        if (tenantId) {
          tenantType = await this.lookupTenantType(tenantId);
        }

        // Fetch user info (email, role) from auth-service - backend is single source of truth
        const userInfo = await this.lookupUserInfo(idTokenString);

        this.setUserInfo({
          userId: currentUser.userId,
          email: userInfo.email,
          tenantId,
          role: userInfo.role,
          tenantType,
          emailVerified: Boolean(idToken['email_verified'])
        });
        return true;
      }
      return false;
    } catch (err) {
      this.clearAuth();
      return false;
    }
  }

  /**
   * Lookup user info from auth-service /me endpoint.
   * Backend is single source of truth for email and role.
   * @param token Optional JWT token to use for authentication
   */
  private async lookupUserInfo(token?: string): Promise<{ email: string; role: string; name: string }> {
    try {
      const options = token
        ? { headers: { Authorization: `Bearer ${token}` } }
        : {};

      const response = await firstValueFrom(
        this.http.get<{ email: string; role: string; name: string; userId: string }>(
          `${environment.apiUrl}/auth/api/v1/auth/me`,
          options
        )
      );
      return {
        email: response?.email || '',
        role: response?.role || 'viewer',
        name: response?.name || ''
      };
    } catch {
      return { email: '', role: 'viewer', name: '' };
    }
  }


  /**
   * Lookup tenant type from platform-service.
   * Uses the tenant info endpoint which returns tenantType.
   */
  private async lookupTenantType(tenantId: string): Promise<'PERSONAL' | 'ORGANIZATION'> {
    try {
      const response = await firstValueFrom(
        this.http.get<{ tenantType: string }>(
          `${environment.apiUrl}/platform/api/v1/tenants/${tenantId}`
        )
      );
      return (response?.tenantType === 'ORGANIZATION' ? 'ORGANIZATION' : 'PERSONAL');
    } catch (error) {
      console.warn('Failed to lookup tenantType, defaulting to PERSONAL', error);
      return 'PERSONAL';
    }
  }


  /**
   * Get the current access token for API calls.
   */
  async getAccessToken(): Promise<string | null> {
    try {
      const session = await fetchAuthSession();
      return session.tokens?.accessToken?.toString() || null;
    } catch {
      return null;
    }
  }

  /**
   * Sign out and clear state.
   */
  async logout(): Promise<void> {
    try {
      await signOut();
    } catch (error) {
      console.error('Logout error', error);
    } finally {
      this.selectedTenantId = null;
      this.clearAuth();
      this.router.navigate(['/auth/login']);
    }
  }

  // ========== Email Verification ==========

  /**
   * Resend verification email to user.
   */
  async resendVerificationEmail(email: string): Promise<void> {
    try {
      const { resendSignUpCode } = await import('aws-amplify/auth');
      await resendSignUpCode({ username: email });
    } catch (error) {
      console.error('Resend verification failed', error);
      throw error;
    }
  }

  /**
   * Confirm signup with verification code.
   */
  async confirmSignUp(email: string, code: string): Promise<void> {
    try {
      const { confirmSignUp } = await import('aws-amplify/auth');
      await confirmSignUp({ username: email, confirmationCode: code });
    } catch (error) {
      console.error('Confirm signup failed', error);
      throw error;
    }
  }

  // ========== Forgot Password Flow ==========

  /**
   * Initiate forgot password flow.
   * Sends a 6-digit verification code to the user's email.
   */
  async forgotPassword(email: string): Promise<{ message: string }> {
    try {
      const response = await firstValueFrom(
        this.http.post<{ message: string }>(
          `${environment.apiUrl}/auth/api/v1/auth/forgot-password`,
          { email }
        )
      );
      return response;
    } catch (error) {
      console.error('Forgot password failed', error);
      throw error;
    }
  }

  /**
   * Reset password with verification code.
   */
  async resetPassword(email: string, code: string, newPassword: string): Promise<{ message: string }> {
    try {
      const response = await firstValueFrom(
        this.http.post<{ message: string }>(
          `${environment.apiUrl}/auth/api/v1/auth/reset-password`,
          { email, code, newPassword }
        )
      );
      return response;
    } catch (error) {
      console.error('Reset password failed', error);
      throw error;
    }
  }

  // ========== Private Helpers ==========

  private setUserInfo(info: UserInfo) {
    this.user.set(info);
    this.isAuthenticated.set(true);
  }

  private clearAuth() {
    this.user.set(null);
    this.isAuthenticated.set(false);
  }
}
