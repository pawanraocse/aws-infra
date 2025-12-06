import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { MessageModule } from 'primeng/message';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterModule,
    CardModule,
    ButtonModule,
    InputTextModule,
    PasswordModule,
    MessageModule
  ],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss']
})
export class LoginComponent {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private router = inject(Router);

  loading = signal(false);
  error = signal<string | null>(null);

  loginForm = this.fb.group({
    username: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]]
  });

  async onSubmit() {
    if (this.loginForm.invalid) return;

    this.loading.set(true);
    this.error.set(null);

    try {
      await this.authService.login({
        username: this.loginForm.value.username!,
        password: this.loginForm.value.password!
      });
      // Navigation handled in authService
    } catch (err: any) {
      this.loading.set(false);

      // Handle unverified user error
      if (err.name === 'UserNotConfirmedException') {
        this.error.set('Please verify your email before logging in. Check your inbox for the verification link.');
        // Navigate to verify-email page with email
        setTimeout(() => {
          this.router.navigate(['/auth/verify-email'], {
            state: { email: this.loginForm.value.username }
          });
        }, 2000);
      } else {
        this.error.set(err.message || 'Login failed. Please check your credentials.');
      }
    } finally {
      this.loading.set(false);
    }
  }
}
