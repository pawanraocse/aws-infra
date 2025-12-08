import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { InputTextModule } from 'primeng/inputtext';
import { environment } from '../../../../environments/environment';

/**
 * Component for email verification page.
 * Shown after user signs up - prompts them to enter verification code.
 */
@Component({
  selector: 'app-verify-email',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, CardModule, ButtonModule, MessageModule, InputTextModule],
  template: `
    <div class="flex align-items-center justify-content-center min-h-screen bg-gray-50">
      <p-card class="w-full max-w-md">
        <div class="text-center">
          <!-- Email Icon -->
          <div class="mb-4">
            <i class="pi pi-envelope text-6xl text-primary"></i>
          </div>

          <!-- Title -->
          <h2 class="text-3xl font-bold mb-3">Verify Your Email</h2>

          <!-- Email Display -->
          <p class="text-gray-600 mb-2">
            We sent a verification code to:
          </p>
          <p class="text-xl font-semibold mb-4">{{ email }}</p>

          <!-- Verification Code Input -->
          <div class="mb-4">
            <label for="code" class="block text-sm font-medium text-gray-700 mb-2">Enter Verification Code</label>
            <input 
              pInputText 
              id="code" 
              [(ngModel)]="verificationCode" 
              class="w-full text-center text-2xl tracking-widest"
              placeholder="000000"
              maxlength="6"
              style="letter-spacing: 0.5em;" />
          </div>

          <!-- Verify Button -->
          <p-button
            label="Verify Email"
            icon="pi pi-check"
            [loading]="verifying"
            [disabled]="verificationCode.length !== 6"
            (onClick)="verifyEmail()"
            styleClass="w-full mb-3">
          </p-button>

          <!-- Success Message -->
          <p-message 
            *ngIf="successMessage" 
            severity="success" 
            [text]="successMessage"
            styleClass="w-full mb-4">
          </p-message>

          <!-- Error Message -->
          <p-message 
            *ngIf="errorMessage" 
            severity="error" 
            [text]="errorMessage"
            styleClass="w-full mb-4">
          </p-message>

          <!-- Resend Button -->
          <div class="mt-3">
            <p-button
              label="Resend Code"
              icon="pi pi-refresh"
              [text]="true"
              [loading]="resending"
              [disabled]="cooldownRemaining > 0"
              (onClick)="resendCode()"
              styleClass="p-button-text">
            </p-button>
          </div>

          <!-- Cooldown Timer -->
          <p *ngIf="cooldownRemaining > 0" class="text-sm text-gray-500 mb-3">
            Please wait {{ cooldownRemaining }} seconds before resending
          </p>

          <!-- Back to Login -->
          <div class="mt-4 pt-4 border-top-1 border-gray-200">
            <a routerLink="/auth/login" class="text-primary cursor-pointer">
              <i class="pi pi-arrow-left mr-2"></i>
              Back to Login
            </a>
          </div>
        </div>
      </p-card>
    </div>
  `,
  styles: [`
    :host ::ng-deep {
      .p-card {
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
      }
      .p-button {
        background: var(--primary-color);
      }
    }
  `]
})
export class VerifyEmailComponent implements OnInit {
  email: string = '';
  tenantId: string = '';
  verificationCode: string = '';
  verifying: boolean = false;
  resending: boolean = false;
  successMessage: string = '';
  errorMessage: string = '';
  cooldownRemaining: number = 0;
  private cooldownInterval: any;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private http: HttpClient
  ) { }

  ngOnInit(): void {
    // Get email and tenantId from router state
    this.email = history.state?.email || this.route.snapshot.queryParams['email'];
    this.tenantId = history.state?.tenantId || '';

    console.log('VerifyEmailComponent initialized:', { email: this.email, tenantId: this.tenantId });

    // If no email provided, redirect to signup
    if (!this.email) {
      console.log('No email found, redirecting to signup');
      this.router.navigate(['/auth/signup/personal']);
    }
  }

  verifyEmail(): void {
    if (this.verificationCode.length !== 6) return;

    this.verifying = true;
    this.errorMessage = '';
    this.successMessage = '';

    const payload = {
      email: this.email,
      code: this.verificationCode,
      tenantId: this.tenantId
    };

    this.http.post(`${environment.apiUrl}/auth/api/v1/auth/signup/verify`, payload)
      .subscribe({
        next: (response: any) => {
          this.verifying = false;
          this.successMessage = 'Email verified successfully! Redirecting to login...';

          // Redirect to login after a short delay
          setTimeout(() => {
            this.router.navigate(['/auth/login'], {
              queryParams: { verified: 'true', email: this.email }
            });
          }, 2000);
        },
        error: (err) => {
          this.verifying = false;
          this.errorMessage = err.error?.message || 'Verification failed. Please try again.';
        }
      });
  }

  resendCode(): void {
    if (this.cooldownRemaining > 0) return;

    this.resending = true;
    this.errorMessage = '';

    this.http.post(`${environment.apiUrl}/auth/api/v1/auth/resend-verification`, { email: this.email })
      .subscribe({
        next: () => {
          this.resending = false;
          this.successMessage = 'Verification code sent!';
          this.startCooldown(60);
        },
        error: (err) => {
          this.resending = false;
          this.errorMessage = err.error?.message || 'Failed to resend code.';
        }
      });
  }

  private startCooldown(seconds: number): void {
    this.cooldownRemaining = seconds;

    if (this.cooldownInterval) {
      clearInterval(this.cooldownInterval);
    }

    this.cooldownInterval = setInterval(() => {
      this.cooldownRemaining--;
      if (this.cooldownRemaining <= 0) {
        clearInterval(this.cooldownInterval);
      }
    }, 1000);
  }

  ngOnDestroy(): void {
    if (this.cooldownInterval) {
      clearInterval(this.cooldownInterval);
    }
  }
}
