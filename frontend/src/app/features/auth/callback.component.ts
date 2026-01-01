import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {ActivatedRoute, Router, RouterModule} from '@angular/router';
import {fetchAuthSession, getCurrentUser} from 'aws-amplify/auth';
import {Hub} from 'aws-amplify/utils';
import {AuthService} from '../../core/auth.service';
import {ProgressSpinnerModule} from 'primeng/progressspinner';
import {MessageModule} from 'primeng/message';
import {environment} from '../../../environments/environment';

/**
 * OAuth2 Callback Component
 *
 * Handles the redirect from Cognito Hosted UI after SSO authentication.
 * Uses Amplify Hub to listen for authentication events and handle code exchange.
 */
@Component({
  selector: 'app-auth-callback',
  standalone: true,
  imports: [CommonModule, ProgressSpinnerModule, MessageModule, RouterModule],
  template: `
    <div class="callback-container">
      <div class="callback-content">
        @if (loading) {
          <p-progressSpinner styleClass="w-4rem h-4rem" strokeWidth="4"></p-progressSpinner>
          <h3 class="mt-4 text-900">Completing Sign In...</h3>
          <p class="text-600">Please wait while we verify your credentials.</p>
        }
        @if (error) {
          <p-message severity="error" [text]="error" styleClass="w-full mb-4"></p-message>
          <a routerLink="/auth/login" class="text-primary cursor-pointer">Back to Login</a>
        }
      </div>
    </div>
  `,
  styles: [`
    .callback-container {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      background: var(--surface-ground);
    }
    .callback-content {
      text-align: center;
      padding: 2rem;
    }
  `]
})
export class AuthCallbackComponent implements OnInit, OnDestroy {
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly authService = inject(AuthService);
  private hubListenerCancel: (() => void) | null = null;

  loading = true;
  error: string | null = null;

  ngOnInit() {
    // Check for error in URL params
    const errorParam = this.route.snapshot.queryParamMap.get('error');
    const errorDescription = this.route.snapshot.queryParamMap.get('error_description');

    if (errorParam) {
      this.error = errorDescription || 'SSO authentication failed';
      this.loading = false;
      return;
    }

    // Listen for Hub auth events
    this.hubListenerCancel = Hub.listen('auth', async ({ payload }) => {
      console.log('[Callback] Hub event:', payload.event);

      switch (payload.event) {
        case 'signedIn':
        case 'signInWithRedirect':
          await this.handleSignInSuccess();
          break;
        case 'signInWithRedirect_failure':
          this.error = 'SSO sign in failed. Please try again.';
          this.loading = false;
          break;
      }
    });

    // Also try direct session check (for cases where Hub event already fired)
    this.checkExistingSession();
  }

  ngOnDestroy() {
    if (this.hubListenerCancel) {
      this.hubListenerCancel();
    }
  }

  private async handleSignInSuccess() {
    try {
      // Get the session to extract user info
      const session = await fetchAuthSession();
      const idToken = session.tokens?.idToken;

      if (idToken) {
        // Extract user info from ID token payload
        const payload = idToken.payload;
        const cognitoUserId = payload['sub'] as string;

        // For SSO users, extract email from identities claim (userId field)
        // The cognito:username for SSO users is prefixed (e.g., "okta-aarohan_pawan.yadav@algosec.com")
        let email = payload['email'] as string || '';
        const identities = payload['identities'] as Array<{ providerName: string; userId: string }> | undefined;

        if (!email && identities && identities.length > 0) {
          // SSO users have email in identities[0].userId
          email = identities[0].userId || '';
        }

        // Fallback to cognito:username if still no email (unlikely for SSO)
        if (!email) {
          const username = payload['cognito:username'] as string || '';
          // Extract email from prefixed username (e.g., "okta-aarohan_pawan.yadav@algosec.com" -> "pawan.yadav@algosec.com")
          if (username.includes('_') && username.includes('@')) {
            email = username.substring(username.indexOf('_') + 1);
          }
        }

        // Extract tenant from identity provider name
        // Format: providerName = "OKTA-aarohan" -> tenantId = "aarohan"
        let tenantId = sessionStorage.getItem('sso_pending_tenant');

        // For social login (Google, etc.), Lambda sets custom:tenantId in JWT
        // This is the personal tenant ID (e.g., "personal-pawanweblink")
        if (!tenantId) {
          const jwtTenantId = payload['custom:tenantId'] as string | undefined;
          if (jwtTenantId) {
            tenantId = jwtTenantId;
            console.log('[Callback] Extracted tenantId from JWT claims:', tenantId);
          }
        }

        if (!tenantId) {
          // Try to extract from identities claim (for org SSO)
          const identities = payload['identities'] as Array<{ providerName: string }> | undefined;
          if (identities && identities.length > 0) {
            const providerName = identities[0].providerName;
            // Extract tenant from provider name (e.g., "OKTA-aarohan" -> "aarohan")
            if (providerName.includes('-')) {
              tenantId = providerName.split('-').slice(1).join('-').toLowerCase();
            }
          }
        }

        if (tenantId && email && cognitoUserId) {
          // JIT provision the SSO user (non-blocking)
          // NOTE: Only use client-side JIT in development. In production,
          // the Lambda handles JIT provisioning before token generation.
          if (!environment.production) {
            console.log('[Callback] JIT provisioning user (dev mode):', { tenantId, email, cognitoUserId });
            await this.authService.jitProvisionSsoUser(tenantId, email, cognitoUserId);
          }
        }
      }

      const isAuthenticated = await this.authService.checkAuth();

      if (isAuthenticated) {
        // Clean up SSO pending tenant
        sessionStorage.removeItem('sso_pending_tenant');

        // Navigate to app
        this.router.navigate(['/app']);
      } else {
        this.error = 'Failed to verify authentication after SSO login';
        this.loading = false;
      }
    } catch (err) {
      console.error('SSO callback error:', err);
      this.error = 'An error occurred completing sign in';
      this.loading = false;
    }
  }

  private async checkExistingSession() {
    // Give Amplify time to process the OAuth callback
    await new Promise(resolve => setTimeout(resolve, 1000));

    try {
      const session = await fetchAuthSession();
      if (session.tokens?.accessToken) {
        console.log('[Callback] Session found, completing sign in');
        await this.handleSignInSuccess();
        return;
      }
    } catch {
      // Session not ready yet
    }

    // Wait a bit more and try again
    await new Promise(resolve => setTimeout(resolve, 2000));

    try {
      const user = await getCurrentUser();
      if (user) {
        console.log('[Callback] User found:', user.userId);
        await this.handleSignInSuccess();
        return;
      }
    } catch {
      // Still not ready
    }

    // Final wait before giving up
    await new Promise(resolve => setTimeout(resolve, 3000));

    try {
      const session = await fetchAuthSession({ forceRefresh: true });
      if (session.tokens?.accessToken) {
        await this.handleSignInSuccess();
      } else {
        this.error = 'Session timeout - could not establish authenticated session. Please try again.';
        this.loading = false;
      }
    } catch (err) {
      console.error('[Callback] Final session check failed:', err);
      this.error = 'Failed to complete SSO sign in. Please try again.';
      this.loading = false;
    }
  }
}
