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
  selector: 'app-signup-personal',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterModule,
    CardModule, ButtonModule, InputTextModule, PasswordModule, MessageModule
  ],
  templateUrl: './signup-personal.component.html',
  styleUrls: ['./signup.common.scss']
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
    password: ['', [Validators.required, Validators.minLength(8)]],
    confirmPassword: ['', Validators.required]
  }, { validators: this.passwordMatchValidator });

  passwordMatchValidator(g: any) {
    return g.get('password')?.value === g.get('confirmPassword')?.value
      ? null : { mismatch: true };
  }

  onSubmit() {
    if (this.signupForm.invalid) return;

    this.loading.set(true);
    this.error.set(null);

    const payload = {
      name: this.signupForm.value.name,
      email: this.signupForm.value.email,
      password: this.signupForm.value.password
    };

    this.http.post(`${environment.apiUrl}/auth/api/v1/auth/signup/personal`, payload)
      .subscribe({
        next: (response: any) => {
          this.loading.set(false);

          if (response.userConfirmed) {
            // User is already confirmed (e.g. auto-verified), go to login
            this.router.navigate(['/auth/login'], {
              queryParams: { verified: 'true', email: this.signupForm.value.email }
            });
          } else {
            // Navigate to email verification page with tenantId
            this.router.navigate(['/auth/verify-email'], {
              state: {
                email: this.signupForm.value.email,
                tenantId: response.tenantId
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
