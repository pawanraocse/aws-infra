import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, tap } from 'rxjs/operators';

export interface UserInfo {
  sub: string;
  email: string;
  name?: string;
  // add more fields as needed
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  constructor(private readonly http: HttpClient) {}
  private readonly authApi = '/auth'; // base URL for auth-service
  readonly user = signal<UserInfo | null>(null);
  readonly isAuthenticated = signal<boolean>(false);

  checkAuth(): Observable<UserInfo | null> {
    return this.http.get<UserInfo>(`${this.authApi}/me`).pipe(
      tap(user => {
        this.user.set(user);
        this.isAuthenticated.set(true);
      }),
      catchError(() => {
        this.user.set(null);
        this.isAuthenticated.set(false);
        return of(null);
      })
    );
  }

  login(credentials: { username: string; password: string }): Observable<any> {
    return this.http.post(`${this.authApi}/login`, credentials).pipe(
      tap(() => this.checkAuth().subscribe()),
      catchError(err => {
        this.user.set(null);
        this.isAuthenticated.set(false);
        return of(err);
      })
    );
  }

  logout(): Observable<any> {
    return this.http.post(`${this.authApi}/logout`, {}).pipe(
      tap(() => {
        this.user.set(null);
        this.isAuthenticated.set(false);
      })
    );
  }
}
