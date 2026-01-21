import {Component, inject, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {Router, RouterModule} from '@angular/router';
import {HttpClient} from '@angular/common/http';
import {CardModule} from 'primeng/card';
import {ButtonModule} from 'primeng/button';
import {InputTextModule} from 'primeng/inputtext';
import {PasswordModule} from 'primeng/password';
import {MessageModule} from 'primeng/message';
import {environment} from '../../../environments/environment';

@Component({
  selector: 'app-signup-organization',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterModule,
    CardModule, ButtonModule, InputTextModule, PasswordModule, MessageModule
  ],
  templateUrl: './signup-organization.component.html',
  styleUrls: ['./signup.common.scss']
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
    password: ['', [Validators.required, Validators.minLength(8)]],
    confirmPassword: ['', Validators.required],
    tier: ['STANDARD', Validators.required]
  }, { validators: this.passwordMatchValidator });

  passwordMatchValidator(g: any) {
    return g.get('password')?.value === g.get('confirmPassword')?.value
      ? null : { mismatch: true };
  }

  onSubmit() {
    if (this.signupForm.invalid) return;

    this.loading.set(true);
    this.error.set(null);

    this.http.post(`${environment.apiUrl}/auth/api/v1/auth/signup/organization`, this.signupForm.value)
      .subscribe({
        next: (response: any) => {
          this.loading.set(false);

          if (response.userConfirmed) {
            // User is already confirmed (rare case), go to login
            this.router.navigate(['/auth/login'], {
              queryParams: { registered: 'true', message: 'Organization created successfully! You can now log in.' }
            });
          } else {
            // Navigate to email verification page with org details
            this.router.navigate(['/auth/verify-email'], {
              state: {
                email: this.signupForm.value.adminEmail,
                tenantId: response.tenantId,
                role: 'admin',
                companyName: this.signupForm.value.companyName
              }
            });
          }
        },
        error: (err) => {
          this.loading.set(false);
          this.error.set(err.error?.message || 'Signup failed. Please try again.');
        }
      });
  }
}
