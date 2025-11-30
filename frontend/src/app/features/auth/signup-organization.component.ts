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
  selector: 'app-signup-organization',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterModule,
    CardModule, ButtonModule, InputTextModule, PasswordModule, MessageModule
  ],
  template: `
    <div class="flex justify-content-center align-items-center min-h-screen bg-blue-50" 
         style="background: linear-gradient(135deg, var(--surface-50) 0%, var(--primary-50) 100%);">
      
      <div class="w-full" style="max-width: 500px;">
        <div class="text-center mb-5">
          <div class="text-3xl font-bold text-900 mb-2">Create Organization</div>
          <div class="text-600 font-medium">Register your company</div>
        </div>

        <p-card styleClass="card-glass shadow-soft p-4">
          <form [formGroup]="signupForm" (ngSubmit)="onSubmit()" class="flex flex-column gap-4">
            
            <div *ngIf="error()" class="mb-2">
              <p-message severity="error" [text]="error()!" styleClass="w-full"></p-message>
            </div>

            <div class="field">
              <label for="companyName" class="font-medium text-900 mb-2 block">Company Name</label>
              <span class="p-input-icon-left w-full">
                <i class="pi pi-building"></i>
                <input pInputText id="companyName" formControlName="companyName" class="w-full" placeholder="Acme Corp" />
              </span>
            </div>

            <div class="grid formgrid p-0 m-0">
              <div class="col-12 md:col-6 pl-0">
                <div class="field">
                  <label for="adminName" class="font-medium text-900 mb-2 block">Admin Name</label>
                  <span class="p-input-icon-left w-full">
                    <i class="pi pi-user"></i>
                    <input pInputText id="adminName" formControlName="adminName" class="w-full" placeholder="Admin User" />
                  </span>
                </div>
              </div>
              <div class="col-12 md:col-6 pr-0">
                <div class="field">
                  <label for="adminEmail" class="font-medium text-900 mb-2 block">Admin Email</label>
                  <span class="p-input-icon-left w-full">
                    <i class="pi pi-envelope"></i>
                    <input pInputText id="adminEmail" formControlName="adminEmail" class="w-full" placeholder="admin@acme.com" />
                  </span>
                </div>
              </div>
            </div>

            <div class="field">
              <label for="tier" class="font-medium text-900 mb-2 block">Subscription Tier</label>
              <select id="tier" formControlName="tier" class="w-full p-inputtext cursor-pointer">
                <option value="STANDARD">Standard (50 Users)</option>
                <option value="PREMIUM">Premium (200 Users)</option>
                <option value="ENTERPRISE">Enterprise (Unlimited)</option>
              </select>
            </div>

            <div class="field">
              <label for="adminPassword" class="font-medium text-900 mb-2 block">Password</label>
              <p-password 
                id="adminPassword" 
                formControlName="adminPassword" 
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
              label="Create Organization" 
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
export class SignupOrganizationComponent {
  private fb = inject(FormBuilder);
  private http = inject(HttpClient);
  private router = inject(Router);

  loading = signal(false);
  error = signal<string | null>(null);

  signupForm = this.fb.group({
    companyName: ['', [Validators.required, Validators.minLength(3)]],
    adminName: ['', [Validators.required, Validators.minLength(2)]],
    adminEmail: ['', [Validators.required, Validators.email]],
    adminPassword: ['', [Validators.required, Validators.minLength(8)]],
    tier: ['STANDARD', Validators.required]
  });

  onSubmit() {
    if (this.signupForm.invalid) return;

    this.loading.set(true);
    this.error.set(null);

    this.http.post(`${environment.apiUrl}/auth/signup/organization`, this.signupForm.value)
      .subscribe({
        next: () => {
          this.loading.set(false);
          // Email is auto-verified in backend, go straight to login
          this.router.navigate(['/auth/login'], {
            queryParams: { registered: 'true', message: 'Organization created successfully! You can now log in.' }
          });
        },
        error: (err) => {
          this.loading.set(false);
          this.error.set(err.error?.message || 'Signup failed. Please try again.');
        }
      });
  }
}
