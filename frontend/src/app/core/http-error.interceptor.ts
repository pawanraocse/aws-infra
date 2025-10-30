import { Injectable, inject } from '@angular/core';
import { AuthService } from './auth.service';
import {
  HttpEvent, HttpInterceptor, HttpHandler, HttpRequest, HttpErrorResponse
} from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';

@Injectable()
export class HttpErrorInterceptor implements HttpInterceptor {
  private authService = inject(AuthService);
  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    return next.handle(req).pipe(
      catchError((error: HttpErrorResponse) => {
        // Centralized error handling: log, notify, handle auth, etc.
        // TODO: Add logic to trigger login dialog on 401/403
        if (error.status === 401 || error.status === 403) {
          // Trigger login dialog or redirect
          this.authService.isAuthenticated.set(false);
          // Optionally: show login modal/dialog here
        }
        // Optionally log error to external service
        return throwError(() => error);
      })
    );
  }
}
