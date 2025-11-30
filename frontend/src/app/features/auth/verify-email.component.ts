import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router, RouterModule, ActivatedRoute } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-verify-email',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterModule,
    CardModule, ButtonModule, InputTextModule, MessageModule
  ],
  template: `
    <div class="flex justify-content-center align-items-center min-h-screen bg-blue-50" 
         style="background: linear-gradient(135deg, var(--surface-50) 0%, var(--primary-50) 100%);">
      
      <div class="w-full" style="max-width: 450px;">
        <div class="text-center mb-5">
          <div class="text-3xl font-bold text-900 mb-2">Verify Your Email</div>
          <div class="text-600 font-medium">We sent a verification codeto {{email()}}</div>
        </div>

        <p-card styleClass="card-glass shadow-soft p-4">
          <form [formGroup]="verifyForm" (ngSubmit)="onSubmit()" class="flex flex-column gap-4">
            
            <div *ngIf="error()" class="mb-2">
              <p-message severity="error" [text]="error()!" styleClass="w-full"></p-message>
            </div>

            <div *ngIf="success()" class="mb-2">
              <p-message severity="success" [text]="success()!" styleClass="w-full"></p-message>
            </div>

            <div class="flex flex-column gap-2">
              <label for="code" class="font-medium text-900">Verification Code</label>
              <span class="p-input-icon-left w-full">
                <i class="pi pi-key"></i>
                <input pInputText id="code" formControlName="code" class="w-full" placeholder="Enter 6-digit code" maxlength="6" />
              </span>
              <small class="text-600">Check your email for the verification code</small>
            </div>

            <p-button 
              type="submit" 
              label="Verify Email" 
              [loading]="loading()" 
              [disabled]="verifyForm.invalid"
              styleClass="w-full p-button-lg">
            </p-button>

            <div class="text-center mt-3">
              <span class="text-600">Didn't receive the code? </span>
              <a (click)="resendCode()" class="font-bold text-primary-600 hover:text-primary-800 cursor-pointer no-underline">Resend</a>
            </div>

            <div class="text-center">
              <a routerLink="/auth/login" class="text-600 hover:text-primary-600 no-underline">Back to Login</a>
            </div>
          </form>
        </p-card>
        
        <div class="text-center mt-4 text-500 text-sm">
          © 2025 Cloud Infra Template. All rights reserved.
        </div>
      </div>
    </div>
  `
})
export class VerifyEmailComponent {
  private fb = inject(FormBuilder);
  private http = inject(HttpClient);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  loading = signal(false);
  error = signal<string | null>(null);
  success = signal<string | null>(null);
  email = signal<string>('');

  verifyForm = this.fb.group({
    code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]]
  });

  ngOnInit() {
    // Get email from query params
    this.route.queryParams.subscribe(params => {
      this.email.set(params['email'] || '');
    });
  }

  onSubmit() {
    if (this.verifyForm.invalid || !this.email()) return;

    this.loading.set(true);
    this.error.set(null);
    this.success.set(null);

    const payload = {
      email: this.email(),
      code: this.verifyForm.value.code
    };

    this.http.post(`${environment.apiUrl}/auth/signup/verify`, payload)
      .subscribe({
        next: () => {
          this.loading.set(false);
          this.success.set('Email verified successfully! Redirecting to login...');
          setTimeout(() => {
            this.router.navigate(['/auth/login'], {
              queryParams: { verified: 'true' }
            });
          }, 2000);
        },
        error: (err) => {
          this.loading.set(false);
          this.error.set(err.error?.message || 'Verification failed. Please try again.');
        }
      });
  }

  resendCode() {
    // TODO: Implement resend code functionality
    alert('Resend code functionality will be implemented');
  }
}
