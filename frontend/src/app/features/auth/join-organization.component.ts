import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { MessageModule } from 'primeng/message';
import { AuthService } from '../../core/auth.service';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-join-organization',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterModule,
    ButtonModule,
    InputTextModule,
    PasswordModule,
    MessageModule
  ],
  template: `
    <div class="flex align-items-center justify-content-center min-h-screen bg-gray-50">
      <div class="surface-card p-4 shadow-2 border-round w-full lg:w-4" style="max-width: 500px">
        <div class="text-center mb-5">
          <h2 class="text-900 text-3xl font-medium mb-3">Join Organization</h2>
          <span class="text-600 font-medium line-height-3">Complete your registration</span>
        </div>

        <div *ngIf="error" class="mb-4">
          <p-message severity="error" [text]="error" styleClass="w-full"></p-message>
        </div>

        <div *ngIf="loading" class="text-center">
          <i class="pi pi-spin pi-spinner text-4xl text-primary"></i>
          <p class="mt-2">Verifying invitation...</p>
        </div>

        <form *ngIf="!loading && isValidToken" [formGroup]="joinForm" (ngSubmit)="onSubmit()">
          <div class="mb-3">
            <label for="name" class="block text-900 font-medium mb-2">Full Name</label>
            <input id="name" type="text" pInputText formControlName="name" class="w-full" />
          </div>

          <div class="mb-3">
            <label for="password" class="block text-900 font-medium mb-2">Password</label>
            <p-password 
              id="password" 
              formControlName="password" 
              [toggleMask]="true" 
              styleClass="w-full" 
              inputStyleClass="w-full">
            </p-password>
          </div>

          <div class="mb-3">
            <label for="confirmPassword" class="block text-900 font-medium mb-2">Confirm Password</label>
            <p-password 
              id="confirmPassword" 
              formControlName="confirmPassword" 
              [toggleMask]="true" 
              [feedback]="false"
              styleClass="w-full" 
              inputStyleClass="w-full">
            </p-password>
            <small *ngIf="joinForm.errors?.['mismatch'] && joinForm.get('confirmPassword')?.touched" class="p-error block mt-1">
              Passwords do not match
            </small>
          </div>

          <button pButton pRipple label="Create Account & Join" class="w-full" [loading]="submitting" [disabled]="joinForm.invalid"></button>
          
          <div class="mt-4 text-center">
            <span class="text-600">Already have an account? </span>
            <a routerLink="/auth/login" [queryParams]="{ token: token }" class="font-medium no-underline text-blue-500 cursor-pointer">Log in to accept</a>
          </div>
        </form>

        <div *ngIf="!loading && !isValidToken" class="text-center">
          <i class="pi pi-times-circle text-4xl text-red-500 mb-3"></i>
          <p class="text-900 font-medium mb-3">Invalid or Expired Invitation</p>
          <p class="text-600 mb-4">This invitation link is no longer valid. Please ask your administrator for a new one.</p>
          <button pButton label="Go to Login" routerLink="/auth/login" class="p-button-text"></button>
        </div>
      </div>
    </div>
  `
})
export class JoinOrganizationComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private fb = inject(FormBuilder);
  private http = inject(HttpClient);
  private authService = inject(AuthService);

  token = '';
  loading = true;
  isValidToken = false;
  submitting = false;
  error = '';

  joinForm = this.fb.group({
    name: ['', Validators.required],
    password: ['', [Validators.required, Validators.minLength(8)]],
    confirmPassword: ['', Validators.required]
  }, { validators: this.passwordMatchValidator });

  ngOnInit() {
    this.token = this.route.snapshot.queryParamMap.get('token') || '';
    if (!this.token) {
      this.loading = false;
      this.isValidToken = false;
      return;
    }
    this.validateToken();
  }

  validateToken() {
    this.http.get(`${environment.apiUrl}/auth/api/v1/invitations/validate?token=${this.token}`)
      .subscribe({
        next: () => {
          this.loading = false;
          this.isValidToken = true;
        },
        error: () => {
          this.loading = false;
          this.isValidToken = false;
        }
      });
  }

  onSubmit() {
    if (this.joinForm.valid) {
      this.submitting = true;
      this.error = '';

      const { name, password } = this.joinForm.value;

      this.http.post(`${environment.apiUrl}/auth/api/v1/invitations/accept`, {
        token: this.token,
        name,
        password
      }).subscribe({
        next: () => {
          this.submitting = false;
          this.router.navigate(['/auth/login'], {
            queryParams: { message: 'Account created successfully. Please log in.' }
          });
        },
        error: (err) => {
          this.submitting = false;
          this.error = err.error?.message || 'Failed to accept invitation';
        }
      });
    }
  }

  passwordMatchValidator(g: any) {
    return g.get('password').value === g.get('confirmPassword').value
      ? null : { mismatch: true };
  }
}
