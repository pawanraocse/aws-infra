import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

/**
 * Error interceptor that handles authentication failures.
 * Excludes certain endpoints from automatic redirect to avoid loops.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
    const router = inject(Router);

    // Endpoints that should not trigger login redirect on 401
    const skipRedirectUrls = [
        '/auth/api/v1/auth/me',       // Called during auth check
        '/auth/api/v1/auth/lookup',   // Tenant lookup
        '/platform/api/v1/tenants/'   // Tenant type lookup
    ];

    return next(req).pipe(
        catchError((error: HttpErrorResponse) => {
            console.log('Error Interceptor caught error:', error.status, error.url);

            // Check if this URL should skip redirect
            const shouldSkipRedirect = skipRedirectUrls.some(url => error.url?.includes(url));

            if ((error.status === 401 || error.status === 403) && !shouldSkipRedirect) {
                console.log('Redirecting to login due to 401/403');
                router.navigate(['/auth/login']);
            }
            return throwError(() => error);
        })
    );
};
