import { Injectable, signal, PLATFORM_ID, inject } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Observable, of, throwError } from 'rxjs';
import { catchError, tap, map } from 'rxjs/operators';
import { Router } from '@angular/router';

export interface UserInfo {
  userId: string;
  email: string;
  tenantId?: string;
  authorities?: string[];
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string | null;
  tokenType: string;
  expiresIn: number;
  userId: string;
  email: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly gatewayUrl = 'http://localhost:8080';
  private readonly ACCESS_TOKEN_KEY = 'access_token';
  private readonly REFRESH_TOKEN_KEY = 'refresh_token';
  private readonly USER_INFO_KEY = 'user_info';
  private readonly platformId = inject(PLATFORM_ID);
  private readonly isBrowser = isPlatformBrowser(this.platformId);

  readonly user = signal<UserInfo | null>(null);
  readonly isAuthenticated = signal<boolean>(false);

  constructor(
    private readonly http: HttpClient,
    private readonly router: Router
  ) {
    // Check if user is already authenticated on service initialization
    // Only access localStorage in browser environment
    if (this.isBrowser) {
      this.loadUserFromStorage();
    }
  }

  /**
   * Redirect to Cognito Hosted UI via Auth Service
   */
  loginWithCognito(): void {
    // Redirect to auth-service which will redirect to Cognito Hosted UI
    // After successful login, auth-service will redirect back to /callback
    window.location.href = `${this.gatewayUrl}/auth/oauth2/authorization/cognito`;
  }

  /**
   * Handle OAuth2 callback - extract JWT from session
   * This is called after successful Cognito authentication
   */
  handleCallback(): Observable<void> {
    // Call auth-service to extract JWT from session
    return this.http.get<TokenResponse>(`${this.gatewayUrl}/auth/tokens`, {
      withCredentials: true // Include session cookie
    }).pipe(
      tap(response => {
        this.storeTokens(response);
        this.setUserInfo({
          userId: response.userId,
          email: response.email
        });
      }),
      map(() => void 0),
      catchError(error => {
        console.error('Failed to extract tokens from session', error);
        return throwError(() => error);
      })
    );
  }

  /**
   * Get user info from stored JWT or session
   */
  getUserInfo(): Observable<UserInfo | null> {
    const token = this.getAccessToken();
    if (!token) {
      return of(null);
    }

    // Decode JWT to get user info (simple base64 decode)
    try {
      const payload = this.decodeJWT(token);
      const userInfo: UserInfo = {
        userId: payload.sub,
        email: payload.email,
        tenantId: payload['cognito:groups']?.[0] || 'default',
        authorities: payload['cognito:groups'] || []
      };
      this.setUserInfo(userInfo);
      return of(userInfo);
    } catch (error) {
      console.error('Failed to decode JWT', error);
      return of(null);
    }
  }

  /**
   * Logout - clear tokens and redirect to login
   */
  logout(): Observable<void> {
    return this.http.post<void>(`${this.gatewayUrl}/auth/logout`, {}, {
      withCredentials: true
    }).pipe(
      tap(() => {
        this.clearAuth();
        this.router.navigate(['login']);
      }),
      catchError(error => {
        // Even if logout fails, clear local auth
        this.clearAuth();
        this.router.navigate(['login']);
        return of(void 0);
      })
    );
  }

  /**
   * Get access token from localStorage
   */
  getAccessToken(): string | null {
    if (!this.isBrowser) return null;
    return localStorage.getItem(this.ACCESS_TOKEN_KEY);
  }

  /**
   * Get refresh token from localStorage
   */
  getRefreshToken(): string | null {
    if (!this.isBrowser) return null;
    return localStorage.getItem(this.REFRESH_TOKEN_KEY);
  }

  /**
   * Check if user is authenticated
   */
  isUserAuthenticated(): boolean {
    const token = this.getAccessToken();
    if (!token) {
      return false;
    }

    // Check if token is expired
    try {
      const payload = this.decodeJWT(token);
      const expirationTime = payload.exp * 1000; // Convert to milliseconds
      return Date.now() < expirationTime;
    } catch {
      return false;
    }
  }

  /**
   * Store tokens in localStorage
   */
  private storeTokens(response: TokenResponse): void {
    if (!this.isBrowser) return;
    localStorage.setItem(this.ACCESS_TOKEN_KEY, response.accessToken);
    if (response.refreshToken) {
      localStorage.setItem(this.REFRESH_TOKEN_KEY, response.refreshToken);
    }
  }

  /**
   * Set user info and update signals
   */
  private setUserInfo(userInfo: UserInfo): void {
    this.user.set(userInfo);
    this.isAuthenticated.set(true);
    if (this.isBrowser) {
      localStorage.setItem(this.USER_INFO_KEY, JSON.stringify(userInfo));
    }
  }

  /**
   * Load user from localStorage on app initialization
   */
  private loadUserFromStorage(): void {
    if (!this.isBrowser) return;
    const userInfoStr = localStorage.getItem(this.USER_INFO_KEY);
    if (userInfoStr && this.isUserAuthenticated()) {
      try {
        const userInfo = JSON.parse(userInfoStr);
        this.user.set(userInfo);
        this.isAuthenticated.set(true);
      } catch {
        this.clearAuth();
      }
    }
  }

  /**
   * Clear all authentication data
   */
  private clearAuth(): void {
    if (this.isBrowser) {
      localStorage.removeItem(this.ACCESS_TOKEN_KEY);
      localStorage.removeItem(this.REFRESH_TOKEN_KEY);
      localStorage.removeItem(this.USER_INFO_KEY);
    }
    this.user.set(null);
    this.isAuthenticated.set(false);
  }

  /**
   * Decode JWT token (simple base64 decode)
   */
  private decodeJWT(token: string): any {
    const parts = token.split('.');
    if (parts.length !== 3) {
      throw new Error('Invalid JWT token');
    }
    const payload = parts[1];
    const decoded = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
    return JSON.parse(decoded);
  }
}
