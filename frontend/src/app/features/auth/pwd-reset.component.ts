import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

// PrimeNG imports
import { CardModule } from 'primeng/card';
import { InputTextModule } from 'primeng/inputtext';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { PasswordModule } from 'primeng/password';

import { AuthService } from '../../core/auth.service';

type FlowStep = 'email' | 'reset';

@Component({
    selector: 'app-password-reset',
    standalone: true,
    imports: [
        CommonModule,
        FormsModule,
        RouterLink,
        CardModule,
        InputTextModule,
        ButtonModule,
        MessageModule,
        PasswordModule
    ],
    template: `
    <div class="flex justify-content-center align-items-center min-h-screen bg-gray-100">
      <p-card [style]="{ width: '400px' }">
        <ng-template pTemplate="header">
          <div class="text-center pt-4">
            <i class="pi pi-lock text-5xl text-primary mb-3"></i>
            <h2 class="m-0 text-xl">{{ step() === 'email' ? 'Forgot Password' : 'Reset Password' }}</h2>
          </div>
        </ng-template>

        <!-- Step 1: Email Input -->
        @if (step() === 'email') {
          <form (ngSubmit)="onRequestCode()" class="flex flex-column gap-3">
            <p class="text-center text-gray-600 text-sm mb-3">
              Enter your email address and we'll send you a verification code to reset your password.
            </p>

            <div class="flex flex-column gap-2">
              <label for="email" class="font-medium">Email</label>
              <input 
                id="email" 
                type="email" 
                pInputText 
                [(ngModel)]="email" 
                name="email"
                placeholder="your&#64;email.com"
                class="w-full"
                required
              />
            </div>

            @if (error()) {
              <p-message severity="error" [text]="error()"></p-message>
            }

            @if (success()) {
              <p-message severity="success" [text]="success()"></p-message>
            }

            <p-button 
              type="submit" 
              label="Send Code" 
              [loading]="loading()"
              styleClass="w-full"
              [disabled]="!email || loading()"
            ></p-button>

            <div class="text-center mt-2">
              <a routerLink="/auth/login" class="text-primary no-underline">
                <i class="pi pi-arrow-left mr-1"></i> Back to Login
              </a>
            </div>
          </form>
        }

        <!-- Step 2: Reset Password -->
        @if (step() === 'reset') {
          <form (ngSubmit)="onResetPassword()" class="flex flex-column gap-3">
            <p class="text-center text-gray-600 text-sm mb-3">
              Enter the 6-digit code sent to <strong>{{ email }}</strong> and your new password.
            </p>

            <div class="flex flex-column gap-2">
              <label for="code" class="font-medium">Verification Code</label>
              <input 
                id="code" 
                type="text" 
                pInputText 
                [(ngModel)]="code" 
                name="code"
                placeholder="123456"
                class="w-full text-center text-2xl tracking-widest"
                maxlength="6"
                required
              />
            </div>

            <div class="flex flex-column gap-2">
              <label for="newPwd" class="font-medium">New Password</label>
              <p-password 
                id="newPwd"
                [(ngModel)]="newPwd" 
                name="newPwd"
                [toggleMask]="true"
                [feedback]="true"
                styleClass="w-full"
                inputStyleClass="w-full"
                required
              ></p-password>
              <small class="text-gray-500">Min 8 characters with uppercase, lowercase, number, and special character</small>
            </div>

            <div class="flex flex-column gap-2">
              <label for="confirmPwd" class="font-medium">Confirm Password</label>
              <p-password 
                id="confirmPwd"
                [(ngModel)]="confirmPwd" 
                name="confirmPwd"
                [toggleMask]="true"
                [feedback]="false"
                styleClass="w-full"
                inputStyleClass="w-full"
                required
              ></p-password>
            </div>

            @if (error()) {
              <p-message severity="error" [text]="error()"></p-message>
            }

            @if (success()) {
              <p-message severity="success" [text]="success()"></p-message>
            }

            <p-button 
              type="submit" 
              label="Reset Password" 
              [loading]="loading()"
              styleClass="w-full"
              [disabled]="!code || !newPwd || !confirmPwd || loading()"
            ></p-button>

            <div class="flex justify-content-between mt-2">
              <a (click)="resendCode()" class="text-primary cursor-pointer no-underline">
                Resend Code
              </a>
              <a (click)="goBack()" class="text-primary cursor-pointer no-underline">
                Change Email
              </a>
            </div>
          </form>
        }
      </p-card>
    </div>
  `,
    styles: [`
    .tracking-widest {
      letter-spacing: 0.5em;
    }
  `]
})
export class PasswordResetComponent {
    private readonly authService = inject(AuthService);
    private readonly router = inject(Router);

    // State
    step = signal<FlowStep>('email');
    loading = signal(false);
    error = signal('');
    success = signal('');

    // Form fields
    email = '';
    code = '';
    newPwd = '';
    confirmPwd = '';

    async onRequestCode() {
        if (!this.email) return;

        this.loading.set(true);
        this.error.set('');
        this.success.set('');

        try {
            await this.authService.forgotPassword(this.email);
            this.success.set('Verification code sent to your email.');
            // Move to reset step
            setTimeout(() => {
                this.step.set('reset');
                this.success.set('');
            }, 1500);
        } catch (err: unknown) {
            const error = err as { error?: { message?: string } };
            this.error.set(error?.error?.message || 'Failed to send verification code. Please try again.');
        } finally {
            this.loading.set(false);
        }
    }

    async onResetPassword() {
        if (!this.code || !this.newPwd || !this.confirmPwd) return;

        if (this.newPwd !== this.confirmPwd) {
            this.error.set('Passwords do not match.');
            return;
        }

        this.loading.set(true);
        this.error.set('');
        this.success.set('');

        try {
            await this.authService.resetPassword(this.email, this.code, this.newPwd);
            this.success.set('Password reset successful! Redirecting to login...');
            setTimeout(() => {
                this.router.navigate(['/auth/login']);
            }, 2000);
        } catch (err: unknown) {
            const error = err as { error?: { message?: string } };
            this.error.set(error?.error?.message || 'Failed to reset password. Please check your code and try again.');
        } finally {
            this.loading.set(false);
        }
    }

    async resendCode() {
        this.loading.set(true);
        this.error.set('');
        this.success.set('');

        try {
            await this.authService.forgotPassword(this.email);
            this.success.set('New verification code sent to your email.');
        } catch (err: unknown) {
            const error = err as { error?: { message?: string } };
            this.error.set(error?.error?.message || 'Failed to resend code.');
        } finally {
            this.loading.set(false);
        }
    }

    goBack() {
        this.step.set('email');
        this.code = '';
        this.newPwd = '';
        this.confirmPwd = '';
        this.error.set('');
        this.success.set('');
    }
}
