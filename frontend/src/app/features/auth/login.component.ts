import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="login-container">
      <div class="login-card">
        <div class="login-header">
          <h1>Welcome to AWS Microservices</h1>
          <p>Sign in to continue</p>
        </div>

        <div class="login-body">
          <button 
            class="btn-cognito" 
            (click)="loginWithCognito()"
            type="button">
            <svg class="icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"></path>
              <polyline points="10 17 15 12 10 7"></polyline>
              <line x1="15" y1="12" x2="3" y2="12"></line>
            </svg>
            Login with AWS Cognito
          </button>

          <div class="info-text">
            <p>You will be redirected to AWS Cognito Hosted UI for secure authentication.</p>
          </div>
        </div>

        <div class="login-footer">
          <p class="text-muted">
            Powered by AWS Cognito • Spring Boot • Angular
          </p>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .login-container {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      padding: 1rem;
    }

    .login-card {
      background: white;
      border-radius: 12px;
      box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
      max-width: 450px;
      width: 100%;
      overflow: hidden;
    }

    .login-header {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      padding: 2.5rem 2rem;
      text-align: center;
    }

    .login-header h1 {
      margin: 0 0 0.5rem 0;
      font-size: 1.75rem;
      font-weight: 600;
    }

    .login-header p {
      margin: 0;
      opacity: 0.9;
      font-size: 1rem;
    }

    .login-body {
      padding: 2.5rem 2rem;
    }

    .btn-cognito {
      width: 100%;
      padding: 1rem 1.5rem;
      background: #FF9900;
      color: white;
      border: none;
      border-radius: 8px;
      font-size: 1.1rem;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.3s ease;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.75rem;
      box-shadow: 0 4px 12px rgba(255, 153, 0, 0.3);
    }

    .btn-cognito:hover {
      background: #ec8800;
      transform: translateY(-2px);
      box-shadow: 0 6px 16px rgba(255, 153, 0, 0.4);
    }

    .btn-cognito:active {
      transform: translateY(0);
    }

    .icon {
      width: 24px;
      height: 24px;
    }

    .info-text {
      margin-top: 1.5rem;
      padding: 1rem;
      background: #f8f9fa;
      border-radius: 6px;
      border-left: 4px solid #667eea;
    }

    .info-text p {
      margin: 0;
      font-size: 0.9rem;
      color: #6c757d;
      line-height: 1.5;
    }

    .login-footer {
      background: #f8f9fa;
      padding: 1.5rem 2rem;
      text-align: center;
      border-top: 1px solid #e9ecef;
    }

    .text-muted {
      margin: 0;
      font-size: 0.85rem;
      color: #6c757d;
    }

    @media (max-width: 576px) {
      .login-header h1 {
        font-size: 1.5rem;
      }

      .login-body {
        padding: 2rem 1.5rem;
      }

      .btn-cognito {
        font-size: 1rem;
        padding: 0.875rem 1.25rem;
      }
    }
  `]
})
export class LoginComponent {
  private authService = inject(AuthService);

  loginWithCognito(): void {
    this.authService.loginWithCognito();
  }
}

