import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../auth.service';

/**
 * Auth Interceptor - Adds JWT token to all HTTP requests
 *
 * This interceptor:
 * 1. Adds Authorization header with JWT token to all requests
 * 2. Skips auth header for login/callback endpoints
 * 3. Handles 401 (Unauthorized) responses by redirecting to login
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  // Skip adding auth header for these endpoints
  const skipAuthUrls = [
    '/auth/login',
    '/auth/oauth2/callback',
    '/auth/tokens',
    '/auth/logout',
    '/oauth2/authorization/cognito'
  ];

  const shouldSkipAuth = skipAuthUrls.some(url => req.url.includes(url));

  if (!shouldSkipAuth) {
    const token = authService.getAccessToken();

    if (token) {
      // Clone the request and add Authorization header
      req = req.clone({
        setHeaders: {
          Authorization: `Bearer ${token}`
        }
      });
    }
  }

  // Handle the request and catch errors
  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && !shouldSkipAuth) {
        // Token expired or invalid, clear auth and redirect to login
        // Don't call logout() to avoid infinite loop
        console.warn('Unauthorized request, redirecting to login');
        router.navigate(['login']);
      }

      return throwError(() => error);
    })
  );
};

