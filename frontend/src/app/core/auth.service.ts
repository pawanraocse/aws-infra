import { Injectable, signal } from '@angular/core';
import { signIn, signOut, getCurrentUser, fetchAuthSession, SignInInput, SignInOutput } from 'aws-amplify/auth';
import { Router } from '@angular/router';

export interface UserInfo {
  userId: string;
  email: string;
  tenantId: string;
  role: string;
  tenantType: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  readonly user = signal<UserInfo | null>(null);
  readonly isAuthenticated = signal<boolean>(false);

  constructor(private router: Router) {
    this.checkAuth();
  }

  /**
   * Check current authentication state and load user info
   */
  async checkAuth(): Promise<boolean> {
    try {
      const currentUser = await getCurrentUser();
      const session = await fetchAuthSession();
      const idToken = session.tokens?.idToken?.payload;

      if (idToken) {
        this.setUserInfo({
          userId: currentUser.userId,
          email: currentUser.signInDetails?.loginId || '',
          tenantId: (idToken['custom:tenantId'] as string) || '',
          role: (idToken['custom:role'] as string) || '',
          tenantType: (idToken['custom:tenantType'] as string) || ''
        });
        return true;
      }
      return false;
    } catch (err) {
      this.clearAuth();
      return false;
    }
  }

  /**
   * Sign in with username and password
   */
  async login(input: SignInInput): Promise<SignInOutput> {
    try {
      const result = await signIn(input);
      if (result.isSignedIn) {
        await this.checkAuth();
        this.router.navigate(['/app']);
      }
      return result;
    } catch (error) {
      console.error('Login failed', error);
      throw error;
    }
  }

  /**
   * Sign out and clear state
   */
  async logout(): Promise<void> {
    try {
      await signOut();
    } catch (error) {
      console.error('Logout error', error);
    } finally {
      this.clearAuth();
      this.router.navigate(['/auth/login']);
    }
  }

  private setUserInfo(info: UserInfo) {
    this.user.set(info);
    this.isAuthenticated.set(true);
  }

  private clearAuth() {
    this.user.set(null);
    this.isAuthenticated.set(false);
  }

  /**
   * Resend verification email to user
   */
  async resendVerificationEmail(email: string): Promise<void> {
    try {
      const { resendSignUpCode } = await import('aws-amplify/auth');
      await resendSignUpCode({ username: email });
    } catch (error) {
      console.error('Resend verification failed', error);
      throw error;
    }
  }

  /**
   * Confirm signup with verification code
   */
  async confirmSignUp(email: string, code: string): Promise<void> {
    try {
      const { confirmSignUp } = await import('aws-amplify/auth');
      await confirmSignUp({ username: email, confirmationCode: code });
    } catch (error) {
      console.error('Confirm signup failed', error);
      throw error;
    }
  }
}
