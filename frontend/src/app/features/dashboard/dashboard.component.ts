import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService, UserInfo } from '../../core/auth.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="dashboard-container">
      <div class="dashboard-header">
        <div class="header-content">
          <h1>Dashboard</h1>
          <button class="btn-logout" (click)="logout()">
            <svg class="icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path>
              <polyline points="16 17 21 12 16 7"></polyline>
              <line x1="21" y1="12" x2="9" y2="12"></line>
            </svg>
            Logout
          </button>
        </div>
      </div>

      <div class="dashboard-content">
        <!-- User Info Card -->
        <div class="card user-card">
          <div class="card-header">
            <h2>User Information</h2>
          </div>
          <div class="card-body">
            @if (user()) {
              <div class="user-info">
                <div class="info-row">
                  <span class="label">User ID:</span>
                  <span class="value">{{ user()?.userId }}</span>
                </div>
                <div class="info-row">
                  <span class="label">Email:</span>
                  <span class="value">{{ user()?.email }}</span>
                </div>
                <div class="info-row">
                  <span class="label">Tenant:</span>
                  <span class="value">{{ user()?.tenantId || 'default' }}</span>
                </div>
                @if (user()?.authorities && user()!.authorities!.length > 0) {
                  <div class="info-row">
                    <span class="label">Roles:</span>
                    <span class="value">{{ user()?.authorities?.join(', ') }}</span>
                  </div>
                }
              </div>
            } @else {
              <p class="text-muted">Loading user information...</p>
            }
          </div>
        </div>

        <!-- Quick Actions -->
        <div class="card actions-card">
          <div class="card-header">
            <h2>Quick Actions</h2>
          </div>
          <div class="card-body">
            <div class="actions-grid">
              <a routerLink="/entries" class="action-button">
                <div class="action-icon">📝</div>
                <div class="action-text">
                  <h3>Manage Entries</h3>
                  <p>View and manage your entries</p>
                </div>
              </a>

              <a routerLink="/entries/new" class="action-button">
                <div class="action-icon">➕</div>
                <div class="action-text">
                  <h3>Create Entry</h3>
                  <p>Add a new entry</p>
                </div>
              </a>
            </div>
          </div>
        </div>

        <!-- Welcome Message -->
        <div class="card welcome-card">
          <div class="card-body">
            <h2>Welcome to AWS Microservices Platform! 🎉</h2>
            <p>
              You are successfully authenticated using AWS Cognito. 
              This application demonstrates a complete microservices architecture with:
            </p>
            <ul>
              <li>OAuth2/OIDC authentication with AWS Cognito</li>
              <li>JWT-based authorization</li>
              <li>Multi-tenant support</li>
              <li>Spring Cloud Gateway</li>
              <li>Service discovery with Eureka</li>
            </ul>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .dashboard-container {
      min-height: 100vh;
      background: #f5f7fa;
    }

    .dashboard-header {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      padding: 2rem;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
    }

    .header-content {
      max-width: 1200px;
      margin: 0 auto;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .dashboard-header h1 {
      margin: 0;
      font-size: 2rem;
      font-weight: 600;
    }

    .btn-logout {
      padding: 0.75rem 1.5rem;
      background: rgba(255, 255, 255, 0.2);
      color: white;
      border: 1px solid rgba(255, 255, 255, 0.3);
      border-radius: 6px;
      font-size: 1rem;
      font-weight: 500;
      cursor: pointer;
      transition: all 0.3s ease;
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }

    .btn-logout:hover {
      background: rgba(255, 255, 255, 0.3);
      transform: translateY(-2px);
    }

    .icon {
      width: 20px;
      height: 20px;
    }

    .dashboard-content {
      max-width: 1200px;
      margin: 0 auto;
      padding: 2rem;
      display: grid;
      gap: 2rem;
      grid-template-columns: repeat(auto-fit, minmax(350px, 1fr));
    }

    .card {
      background: white;
      border-radius: 12px;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
      overflow: hidden;
    }

    .card-header {
      padding: 1.5rem;
      border-bottom: 1px solid #e9ecef;
      background: #f8f9fa;
    }

    .card-header h2 {
      margin: 0;
      font-size: 1.25rem;
      font-weight: 600;
      color: #333;
    }

    .card-body {
      padding: 1.5rem;
    }

    .user-info {
      display: flex;
      flex-direction: column;
      gap: 1rem;
    }

    .info-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 0.75rem;
      background: #f8f9fa;
      border-radius: 6px;
    }

    .label {
      font-weight: 600;
      color: #6c757d;
    }

    .value {
      color: #333;
      font-family: monospace;
      font-size: 0.9rem;
    }

    .actions-grid {
      display: grid;
      gap: 1rem;
    }

    .action-button {
      display: flex;
      align-items: center;
      gap: 1rem;
      padding: 1.5rem;
      background: #f8f9fa;
      border-radius: 8px;
      text-decoration: none;
      color: inherit;
      transition: all 0.3s ease;
      border: 2px solid transparent;
    }

    .action-button:hover {
      background: #e9ecef;
      border-color: #667eea;
      transform: translateY(-2px);
    }

    .action-icon {
      font-size: 2.5rem;
    }

    .action-text h3 {
      margin: 0 0 0.25rem 0;
      font-size: 1.1rem;
      color: #333;
    }

    .action-text p {
      margin: 0;
      font-size: 0.9rem;
      color: #6c757d;
    }

    .welcome-card {
      grid-column: 1 / -1;
    }

    .welcome-card h2 {
      margin: 0 0 1rem 0;
      color: #333;
      font-size: 1.5rem;
    }

    .welcome-card p {
      margin: 0 0 1rem 0;
      color: #6c757d;
      line-height: 1.6;
    }

    .welcome-card ul {
      margin: 0;
      padding-left: 1.5rem;
      color: #6c757d;
      line-height: 1.8;
    }

    .text-muted {
      color: #6c757d;
      font-style: italic;
    }

    @media (max-width: 768px) {
      .dashboard-content {
        grid-template-columns: 1fr;
        padding: 1rem;
      }

      .header-content {
        flex-direction: column;
        gap: 1rem;
        align-items: flex-start;
      }

      .dashboard-header h1 {
        font-size: 1.5rem;
      }
    }
  `]
})
export class DashboardComponent implements OnInit {
  private authService = inject(AuthService);
  
  user = signal<UserInfo | null>(null);

  ngOnInit(): void {
    // Load user info
    this.authService.getUserInfo().subscribe({
      next: (userInfo) => {
        this.user.set(userInfo);
      },
      error: (err) => {
        console.error('Failed to load user info', err);
      }
    });
  }

  logout(): void {
    if (confirm('Are you sure you want to logout?')) {
      this.authService.logout().subscribe();
    }
  }
}

