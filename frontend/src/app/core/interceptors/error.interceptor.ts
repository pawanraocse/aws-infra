import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
    const router = inject(Router);

    return next(req).pipe(
        catchError((error: HttpErrorResponse) => {
            console.log('Error Interceptor caught error:', error.status, error.url);
            if (error.status === 401 || error.status === 403) {
                console.log('Redirecting to login due to 401/403');
                // Redirect to login on authentication failure
                router.navigate(['/auth/login']);
            }
            return throwError(() => error);
        })
    );
};
