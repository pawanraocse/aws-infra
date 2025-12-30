import {Component, inject, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormBuilder, FormsModule, ReactiveFormsModule, Validators} from '@angular/forms';
import {Router, RouterModule} from '@angular/router';
import {AuthService} from '../../core/auth.service';
import {AuthExceptionType, getAuthErrorMessage, TenantInfo, TenantLookupResult} from '../../core/models';
import {CardModule} from 'primeng/card';
import {ButtonModule} from 'primeng/button';
import {InputTextModule} from 'primeng/inputtext';
import {PasswordModule} from 'primeng/password';
import {MessageModule} from 'primeng/message';
import {ProgressSpinnerModule} from 'primeng/progressspinner';

/**
 * Login flow states for the multi-step state machine.
 */
type LoginStep = 'email' | 'select-tenant' | 'password';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    RouterModule,
    CardModule,
    ButtonModule,
    InputTextModule,
    PasswordModule,
    MessageModule,
    ProgressSpinnerModule
  ],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss']
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  // ========== State Machine ==========

  /** Current step in the login flow */
  currentStep = signal<LoginStep>('email');

  /** Loading state */
  loading = signal(false);

  /** Error message to display */
  error = signal<string | null>(null);

  /** Success message (e.g., for verification redirect) */
  success = signal<string | null>(null);

  /** Lookup result from tenant query */
  lookupResult = signal<TenantLookupResult | null>(null);

  /** Selected tenant for login */
  selectedTenant = signal<TenantInfo | null>(null);

  // ========== Forms ==========

  /** Email form for step 1 */
  emailForm = this.fb.group({
    email: ['', [Validators.required, Validators.email]]
  });

  /** Password form for step 3 */
  passwordForm = this.fb.group({
    password: ['', [Validators.required, Validators.minLength(8)]]
  });

  // ========== SSO Login ==========

  /** Show SSO organization input */
  showSsoInput = signal(false);

  /** SSO tenant/organization name */
  ssoTenantName = '';

  // ========== Step 1: Email Lookup ==========

  /**
   * Handle email submission and lookup tenants.
   */
  async onEmailSubmit() {
    if (this.emailForm.invalid) return;

    const email = this.emailForm.value.email!;
    this.loading.set(true);
    this.error.set(null);

    try {
      const result = await this.authService.lookupTenants(email);
      this.lookupResult.set(result);

      if (result.tenants.length === 0) {
        // No tenants found - user needs to sign up
        this.error.set(getAuthErrorMessage(AuthExceptionType.NO_TENANTS_FOUND));
      } else if (result.tenants.length === 1) {
        // Single tenant - auto-select and go to password
        this.selectTenant(result.tenants[0]);
        this.currentStep.set('password');
      } else {
        // Multiple tenants - show selector
        this.currentStep.set('select-tenant');
      }
    } catch (err) {
      this.error.set('Failed to lookup account. Please try again.');
    } finally {
      this.loading.set(false);
    }
  }

  /**
   * Select a tenant from the list.
   */
  selectTenant(tenant: TenantInfo) {
    this.selectedTenant.set(tenant);

    // Check if SSO is enabled for this tenant
    if (tenant.ssoEnabled) {
      // Redirect to SSO login via Cognito Hosted UI
      this.authService.loginWithSSO(tenant);
      return;
    }

    this.currentStep.set('password');
    this.error.set(null);
  }

  /**
   * Handle back button from tenant selection.
   */
  goBackToEmail() {
    this.currentStep.set('email');
    this.lookupResult.set(null);
    this.selectedTenant.set(null);
    this.error.set(null);
  }

  // ========== Step 3: Password & Authentication ==========

  /**
   * Handle password submission and login.
   */
  async onPasswordSubmit() {
    if (this.passwordForm.invalid) return;

    const email = this.emailForm.value.email!;
    const password = this.passwordForm.value.password!;
    const tenant = this.selectedTenant();

    if (!tenant) {
      this.error.set('Please select a workspace first.');
      this.currentStep.set('select-tenant');
      return;
    }

    this.loading.set(true);
    this.error.set(null);

    try {
      await this.authService.loginWithTenant({
        email,
        password,
        selectedTenantId: tenant.tenantId
      });
      // Navigation handled in authService
    } catch (err: unknown) {
      this.loading.set(false);

      const errorWithCode = err as { code?: AuthExceptionType; message?: string };

      // Handle specific error cases
      if (errorWithCode.code === AuthExceptionType.USER_NOT_CONFIRMED) {
        this.success.set('Redirecting to verification...');
        setTimeout(() => {
          this.router.navigate(['/auth/verify-email'], {
            state: { email: this.emailForm.value.email }
          });
        }, 1500);
      } else {
        this.error.set(errorWithCode.message || 'Login failed. Please check your credentials.');
      }
    } finally {
      this.loading.set(false);
    }
  }

  /**
   * Handle back button from password step.
   */
  goBackToTenantSelection() {
    const result = this.lookupResult();

    if (result && result.tenants.length > 1) {
      this.currentStep.set('select-tenant');
    } else {
      this.goBackToEmail();
    }

    this.passwordForm.reset();
    this.error.set(null);
  }

  // ========== UI Helpers ==========

  /**
   * Get display name for a tenant.
   */
  getTenantDisplayName(tenant: TenantInfo): string {
    if (tenant.tenantType === 'PERSONAL') {
      return 'Personal Workspace';
    }
    return tenant.companyName || tenant.tenantName;
  }

  /**
   * Get subtitle for a tenant (role hint).
   */
  getTenantSubtitle(tenant: TenantInfo): string {
    const roleLabels: Record<string, string> = {
      'owner': 'Owner',
      'admin': 'Administrator',
      'member': 'Member',
      'guest': 'Guest'
    };
    return roleLabels[tenant.roleHint] || tenant.roleHint;
  }

  /**
   * Get icon class for tenant type.
   */
  getTenantIcon(tenant: TenantInfo): string {
    return tenant.tenantType === 'PERSONAL' ? 'pi-user' : 'pi-building';
  }

  // ========== SSO Methods ==========

  /**
   * Show SSO organization input.
   */
  showSsoTenantInput() {
    this.showSsoInput.set(true);
    this.error.set(null);
  }

  /**
   * Cancel SSO input and go back to email form.
   */
  cancelSsoInput() {
    this.showSsoInput.set(false);
    this.ssoTenantName = '';
    this.error.set(null);
  }

  /**
   * Login with SSO using the organization name.
   * Looks up the tenant by name and redirects to SSO.
   */
  async loginWithSsoTenant() {
    if (!this.ssoTenantName.trim()) {
      this.error.set('Please enter your organization name');
      return;
    }

    this.loading.set(true);
    this.error.set(null);

    try {
      // Create a minimal tenant info for SSO redirect
      // The tenant ID is typically the organization name (sanitized)
      const tenantId = this.ssoTenantName.toLowerCase().trim().replace(/[^a-z0-9]/g, '');

      const tenant: TenantInfo = {
        tenantId: tenantId,
        tenantName: this.ssoTenantName,
        tenantType: 'ORGANIZATION',
        ssoEnabled: true,
        roleHint: 'member',
        isOwner: false,
        isDefault: false,
        // Cognito provider name follows our convention
        cognitoProviderName: `OKTA-${tenantId}`
      };

      // Redirect to SSO
      this.authService.loginWithSSO(tenant);
    } catch (err) {
      this.loading.set(false);
      this.error.set('Failed to initiate SSO login. Please try again.');
    }
  }
}
