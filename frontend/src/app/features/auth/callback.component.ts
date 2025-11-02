import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-callback',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="callback-container">
      <div class="callback-card">
        @if (loading) {
          <div class="loading-state">
            <div class="spinner"></div>
            <h2>Completing authentication...</h2>
            <p>Please wait while we securely log you in.</p>
          </div>
        }

        @if (error) {
          <div class="error-state">
            <div class="error-icon">⚠️</div>
            <h2>Authentication Failed</h2>
            <p>{{ errorMessage }}</p>
            <button class="btn-retry" (click)="retry()">
              Try Again
            </button>
          </div>
        }
      </div>
    </div>
  `,
  styles: [`
    .callback-container {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      padding: 1rem;
    }

    .callback-card {
      background: white;
      border-radius: 12px;
      box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
      max-width: 450px;
      width: 100%;
      padding: 3rem 2rem;
      text-align: center;
    }

    .loading-state h2 {
      margin: 1.5rem 0 0.5rem 0;
      color: #333;
      font-size: 1.5rem;
    }

    .loading-state p {
      margin: 0;
      color: #6c757d;
      font-size: 1rem;
    }

    .spinner {
      width: 60px;
      height: 60px;
      margin: 0 auto;
      border: 4px solid #f3f3f3;
      border-top: 4px solid #667eea;
      border-radius: 50%;
      animation: spin 1s linear infinite;
    }

    @keyframes spin {
      0% { transform: rotate(0deg); }
      100% { transform: rotate(360deg); }
    }

    .error-state {
      color: #dc3545;
    }

    .error-icon {
      font-size: 4rem;
      margin-bottom: 1rem;
    }

    .error-state h2 {
      margin: 0 0 1rem 0;
      color: #dc3545;
      font-size: 1.5rem;
    }

    .error-state p {
      margin: 0 0 2rem 0;
      color: #6c757d;
      font-size: 1rem;
    }

    .btn-retry {
      padding: 0.75rem 2rem;
      background: #667eea;
      color: white;
      border: none;
      border-radius: 6px;
      font-size: 1rem;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.3s ease;
    }

    .btn-retry:hover {
      background: #5568d3;
      transform: translateY(-2px);
    }
  `]
})
export class CallbackComponent implements OnInit {
  private authService = inject(AuthService);
  private router = inject(Router);

  loading = true;
  error = false;
  errorMessage = '';

  ngOnInit(): void {
    // The auth-service has already handled the OAuth2 callback
    // and created a session. Now we need to extract the JWT from the session.
    this.handleCallback();
  }

  private handleCallback(): void {
    this.authService.handleCallback().subscribe({
      next: () => {
        // Successfully extracted JWT and stored it
        // Get user info and navigate to dashboard
        this.authService.getUserInfo().subscribe({
          next: () => {
            this.loading = false;
            this.router.navigate(['dashboard']);
          },
          error: (err) => {
            console.error('Failed to get user info', err);
            this.showError('Failed to retrieve user information.');
          }
        });
      },
      error: (err) => {
        console.error('Callback handling failed', err);
        this.showError('Authentication failed. Please try again.');
      }
    });
  }

  private showError(message: string): void {
    this.loading = false;
    this.error = true;
    this.errorMessage = message;
  }

  retry(): void {
    this.router.navigate(['login']);
  }
}

