import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-callback',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './callback.component.html',
  styleUrls: ['./callback.component.scss']
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
