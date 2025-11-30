import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { MessageModule } from 'primeng/message';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-signup-personal',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterModule,
    CardModule, ButtonModule, InputTextModule, PasswordModule, MessageModule
  ],
  template: `
    <div class="flex justify-content-center align-items-center min-h-screen bg-blue-50" 
         style="background: linear-gradient(135deg, var(--surface-50) 0%, var(--primary-50) 100%);">
      
      <div class="w-full" style="max-width: 450px;">
        <div class="text-center mb-5">
          <div class="text-3xl font-bold text-900 mb-2">Create Account</div>
          <div class="text-600 font-medium">Join us as a personal user</div>
        </div>

        <p-card styleClass="card-glass shadow-soft p-4">
          <form [formGroup]="signupForm" (ngSubmit)="onSubmit()" class="flex flex-column gap-4">
            
            <div *ngIf="error()" class="mb-2">
              <p-message severity="error" [text]="error()!" styleClass="w-full"></p-message>
            </div>

            <div class="flex flex-column gap-2">
              <label for="name" class="font-medium text-900">Full Name</label>
              <span class="p-input-icon-left w-full">
                <i class="pi pi-user"></i>
                <input pInputText id="name" formControlName="name" class="w-full" placeholder="John Doe" />
              </span>
            </div>

            <div class="flex flex-column gap-2">
              <label for="email" class="font-medium text-900">Email Address</label>
              <span class="p-input-icon-left w-full">
                <i class="pi pi-envelope"></i>
                <input pInputText id="email" formControlName="email" class="w-full" placeholder="you@example.com" />
              </span>
            </div>

            <div class="flex flex-column gap-2">
              <label for="password" class="font-medium text-900">Password</label>
              <p-password 
                id="password" 
                formControlName="password" 
                [toggleMask]="true"
                styleClass="w-full"
                inputStyleClass="w-full"
                placeholder="Min 8 chars">
                <ng-template pTemplate="header">
                  <div class="font-semibold text-sm mb-2">Pick a password</div>
                </ng-template>
                <ng-template pTemplate="footer">
                  <p class="mt-2 text-sm text-600">Suggestions</p>
                  <ul class="pl-2 ml-2 mt-0 text-sm text-600" style="line-height: 1.5">
                      <li>At least one lowercase</li>
                      <li>At least one uppercase</li>
                      <li>At least one numeric</li>
                      <li>Minimum 8 characters</li>
                  </ul>
                </ng-template>
              </p-password>
            </div>

            <p-button 
              type="submit" 
              label="Create Account" 
              [loading]="loading()" 
              [disabled]="signupForm.invalid"
              styleClass="w-full p-button-lg">
            </p-button>

            <div class="text-center mt-3">
              <span class="text-600">Already have an account? </span>
              <a routerLink="/auth/login" class="font-bold text-primary-600 hover:text-primary-800 cursor-pointer no-underline">Sign In</a>
            </div>
          </form>
        </p-card>
        
        <div class="text-center mt-4 text-500 text-sm">
          &copy; 2025 Cloud Infra Template. All rights reserved.
        </div>
      </div>
    </div>
  `
})
export class SignupPersonalComponent {
  private fb = inject(FormBuilder);
  private http = inject(HttpClient);
  private router = inject(Router);

  loading = signal(false);
  error = signal<string | null>(null);

  signupForm = this.fb.group({
    name: ['', [Validators.required, Validators.minLength(2)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]]
  });

  onSubmit() {
    if (this.signupForm.invalid) return;

    this.loading.set(true);
    this.error.set(null);

    const payload = {
      name: this.signupForm.value.name,
      email: this.signupForm.value.email,
      password: this.signupForm.value.password
    };

    this.http.post(`${environment.apiUrl}/auth/signup/personal`, payload)
      .subscribe({
        next: () => {
          this.loading.set(false);
          // Email is auto-verified in backend, go straight to login
          this.router.navigate(['/auth/login'], {
            queryParams: { registered: 'true', message: 'Account created successfully! You can now log in.' }
          });
        },
        error: (err) => {
          this.loading.set(false);
          this.error.set(err.error?.message || 'Signup failed. Please try again.');
        }
      });
  }
}
