import { Injectable, inject, signal } from '@angular/core';
import { signIn, signOut, getCurrentUser, fetchAuthSession, SignInOutput } from 'aws-amplify/auth';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import {
  UserInfo,
  TenantInfo,
  TenantLookupResult,
  LoginWithTenantInput,
  AuthExceptionType,
  classifyAuthError,
  getAuthErrorMessage
} from './models';
import { environment } from '../../environments/environment';

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

  // ========== Session Management ==========

  /**
   * Check current authentication state and load user info.
   */
  async checkAuth(): Promise<boolean> {
    try {
      const currentUser = await getCurrentUser();
      const session = await fetchAuthSession();
      const idToken = session.tokens?.idToken?.payload;

      if (idToken) {
        this.setUserInfo({
          userId: currentUser.userId,
          email: currentUser.signInDetails?.loginId || '',
          tenantId: (idToken['custom:tenantId'] as string) || '',
          role: (idToken['custom:role'] as string) || '',
          tenantType: ((idToken['custom:tenantType'] as string) || 'PERSONAL') as 'PERSONAL' | 'ORGANIZATION',
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
